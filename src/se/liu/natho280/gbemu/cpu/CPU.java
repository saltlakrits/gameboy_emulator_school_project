package se.liu.natho280.gbemu.cpu;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.Main;
import se.liu.natho280.gbemu.debugger.RegisterListener;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


/**
 * 'Main' class for use in fetching, decoding and executing instructions from the ROM.
 */
public class CPU implements Serializable {
    // this is the emulator class, that will have the logic one step above the hardware (kind of)
    // this class is what ultimately utilizes the smaller hardware components to fetch, decode and execute instructions.
    private transient Memory memory;
    private Registers regs; // FIXME We are doing this separately, fix!
    private int cycles = 0;
    // interrupt master enable flag, true -> we will call interrupt handlers
    private boolean interruptsEnabled = false;

    private boolean halted = false;
    private int haltBugCounter = 0;

    // For synchronizing
    private Object lock = new Object();

    /**
     * The rom path is the program parameter. The Memory is set in the Main file.
     * @param romPath
     * @param memory
     * @see Main
     */
    public CPU(Memory memory, Registers regs) {
        this.memory = memory;
        this.regs = regs;
    }

    /**
     * When loading a different ROM, we need to reset the CPU
     * to the initial state.
     */
    public void reInitializeCPU() {
        this.regs.initRegisters();
        this.interruptsEnabled = false;
        this.halted = false;
        this.haltBugCounter = 0;
        this.cycles = 0;
        setUpBoot();
    }

    public Registers getRegisters() {
        return this.regs.copy();
    }

    public void restoreState(final SerializationWrapper serializationWrapper) {
        CPU serializedCPU = serializationWrapper.getCPU();
        this.halted = serializedCPU.halted;
        this.cycles = serializedCPU.cycles;
        this.haltBugCounter = serializedCPU.haltBugCounter;
        this.interruptsEnabled = serializedCPU.interruptsEnabled;

        this.regs.restoreState(serializedCPU.getRegisters());

//        List<RegisterListener> oldListeners = regs.getListeners();
//        for (RegisterListener listener : oldListeners) {
//            this.regs.addRegisterListener(listener);
//        }
    }

    public void pushPC() {
        // push PC to stack
        regs.decSP();
        memory.write(regs.get(Reg.SP), (regs.get(Reg.PC) >>> 8));
        regs.decSP();
        memory.write(regs.get(Reg.SP), (regs.get(Reg.PC)) & 0xFF);
    }

    public void popPC() {
        // pop PC from stack
        int newPC = memory.read(regs.get(Reg.SP));
        regs.incSP();
        newPC |= memory.read(regs.get(Reg.SP)) << 8;
        regs.incSP();
        regs.set(Reg.PC, newPC);
    }

    private void unsetInterruptFlagBit(int bit, int interruptFlags) {
        memory.write(0xFF0F, (interruptFlags & ((1 << bit) ^ 0xFF)));
    }

    /**
     * When an interrupt is called, the program counter is set to an appropriate jump vector.
     * This matches the bit index in the interrupt flags at 0xFF0F/0xFFFF to the matching address.
     * @param bit the index of the interrupt flag
     * @return a jump vector
     * @see <a href=https://gbdev.io/pandocs/Interrupt_Sources.html>Pan Docs - Interrupt Sources</a>
     */
    private int matchInterruptAddress(int bit) {
        switch (bit) {
            case 0:
                return 0x40;
            case 1:
                return 0x48;
            case 2:
                return 0x50;
            case 3:
                return 0x58;
            case 4:
                return 0x60;
            default:
                CuteLogger.log(Level.SEVERE, "Tried to match interrupt address with bit " + bit +
                                             ", faulty");
                System.exit(-1);
                return 0; // silence language server
        }
    }

    /**
     * Loops through the possible interrupts and matches one that is both enabled in 0xFFFF
     * as well as requested in 0xFF0F to an interrupt handler, and sets the program counter
     * to it after pushing the current value of it to the stack.
     * @see <a href=https://gbdev.io/pandocs/Interrupts.html>Pan Docs - Interrupts</a>
     */
    private void handleInterrupts() {
        int interruptEnableFlags = memory.read(0xFFFF);
        int interruptFlags = memory.read(0xFF0F);

        if (!interruptsEnabled && !halted) return;

        for (int i = 0; i < 5; i++) {
            // check if interrupt is enabled, and then if it is requested
            // if so, call handler
            if (((interruptEnableFlags & (1 << i)) != 0) &&
                    ((interruptFlags & (1 << i)) != 0)) {

                // unhalt
                this.halted = false;

                if (interruptsEnabled) {
                    interruptsEnabled = false;
                    // call the handler
                    pushPC();
                    regs.set(Reg.PC, matchInterruptAddress(i));
                    // unset
                    unsetInterruptFlagBit(i, interruptFlags);
                    cycles += 5;
                }

                return; // early return if we found an interrupt
            }
        }
    }

    /**
     * These are all the important values (values that games may rely on being there) the boot rom sets.
     * If this is run at startup, the boot rom is skippable.
     */
    public void setUpBoot() {
        regs.set(Reg.A, 0x01);
        regs.set(Reg.F, 0xB0);

        regs.set(Reg.B, 0x0);
        regs.set(Reg.C, 0x13);

        regs.set(Reg.D, 0x0);
        regs.set(Reg.E, 0xD8);

        regs.set(Reg.H, 0x01);
        regs.set(Reg.L, 0x4D);

        regs.set(Reg.PC, 0x100);

        memory.write(0xFF50, 1);
    }

    /**
     * If not halted, returns false. If halted, checks if we have waiting, enabled interrupts
     * (which should un-halt the CPU).
     * @return
     */
    public boolean getHalted() {
        if (!halted) {
            return false;
        } else {
            return ((memory.read(0xFFFF) & memory.read(0xFF0F)) == 0);
        }
    }

    public Object lock() {
        return this.lock;
    }

    /**
     * <p>The meat of the CPU which runs the fetch-decode-execute cycle. In reality, this runs more than one CPU cycle
     * very often, and will return how many cycles it ran. This is for timing, and used in Main. It is hard to read and
     * interpret what this method is doing, since emulators often boil down to a gigantic switch statement. See
     * <a href=https://meganesu.github.io/generate-gb-opcodes/>Meganesu Game Boy CPU Instructions</a>
     * for an interactive chart of the instructions, or
     * see <a href=https://gbdev.io/pandocs/CPU_Instruction_Set.html>Pan Docs - CPU Instruction Set</a> for a
     * different breakdown.</p>
     *
     * <p>Some of the longer and/or reusable instructions have been broken out
     * into separate methods below. There is also a second chart of 256 16-bit instructions that was broken out into
     * another method, ({@link #bigInstruction}).</p>
     * @return cycles it took to finish an instruction
     */
    public int runCycle() {
        if (haltBugCounter == 1) regs.addPC(-1);

        // Check & handle interrupts -- I think this should work?
        handleInterrupts();
        if (halted) return 0;

        int instruction = memory.read(regs.get(Reg.PC));

        regs.addPC(1);
        cycles++;

        // have instruction, increased PC, now to decode instruction

        // fetch carry flag
        final int carryFlag = regs.getCarryFlag();

        int firstNibble = (instruction & 0xF0) >> 4;
        int secondNibble = (instruction & 0x0F);
        // if d8 is used, inc pc!
        int d8 = memory.read(regs.get(Reg.PC));
        // sometimes, if current instruction is 0xCB, d8 DOES correspond to an instruction. we don't want to call it d8 then.
        // s8, signed, inc pc if used!
        int s8 = (byte) memory.read(regs.get(Reg.PC));
        // if d16 is used, inc pc by 2!
        int d16 = (memory.read(regs.get(Reg.PC) + 1) << 8) | memory.read(regs.get(Reg.PC));

        // Certain instructions use the second nibble to choose a register, here called a source register
        // (as it is usually the source register for a move, add, or similar instruction)
        // To simplify the big switch statement, we determine the possibilities up-front
        // and assign the correct value here.
        Reg sourceReg = Registers.getSourceRegByNibble(secondNibble);
        int sourceRegValue;
        if (sourceReg == Reg.HL) {
            // if HL is the source register, we should use the value that it is pointing to in memory
            sourceRegValue = memory.read(regs.get(Reg.HL));
        } else {
            sourceRegValue = regs.get(sourceReg);
        }

        switch (firstNibble) {
            case 0x0:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 1, NOP, i.e. do nothing for one cycle
                        break;
                    case 0x1:
                        // cycles: 3, bytes: 3, LD r16, d16
                        ldR16d16(firstNibble, d16);
                        break;
                    case 0x2:
                        // cycles: 2, LD (BC), A
                        ldR16PtrA(firstNibble);
                        break;
                    case 0x3:
                        // cycles: 2, INC BC
                        regs.set(Reg.BC, regs.get(Reg.BC) + 1);
                        cycles++;
                        break;
                    case 0x4:
                        // cycles: 1, INC B
                        incR8(Reg.B);
                        break;
                    case 0x5:
                        // cycles: 1, DEC B
                        decR8(Reg.B);
                        break;
                    case 0x6:
                        // cycles: 2, LD B, d8
                        regs.set(Reg.B, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 1, rotate the bits of A left, setting carry flag to the bit that loops around
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag((regs.get(Reg.A) & 0x80) >> 7 == 1);
                        regs.set(Reg.A, regs.get(Reg.A) << 1 | ((regs.get(Reg.A) & 0x80) >> 7));
                        break;
                    case 0x8:
                        // cycles: 3, LD (a16), SP
                        memory.write(d16, regs.get(Reg.SP) & 0xFF);
                        memory.write(d16 + 0x1, (regs.get(Reg.SP) & 0xFF00) >> 8);
                        regs.addPC(2);
                        cycles += 4;
                        break;
                    case 0x9:
                        // cycles: 2, ADD HL, BC
                        addHLr16(Reg.BC);
                        break;
                    case 0xA:
                        // cycles: 2, LD A, (BC)
                        ldAR16Ptr(firstNibble);
                        break;
                    case 0xB:
                        // DEC r16
                        regs.set(Reg.BC, regs.get(Reg.BC) - 1);
                        cycles++;
                        break;
                    case 0xC:
                        // cycles: 1, INC C
                        incR8(Reg.C);
                        break;
                    case 0xD:
                        // cycles: 1, DEC C
                        decR8(Reg.C);
                        break;
                    case 0xE:
                        // cycles: 2, LD C, d8
                        regs.set(Reg.C, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 1, rotate the bits of A right, setting carry flag to the bit that loops around
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag((regs.get(Reg.A) & 0x1) == 1);
                        regs.set(Reg.A, regs.get(Reg.A) >> 1 | ((regs.get(Reg.A) & 0x1) << 7));
                        break;
                }
                break;
            case 0x1:
                switch (secondNibble) {
                    case 0x0:
                        regs.addPC(1);
                        if (d8 == 0x00) {
                            // TODO!
                            // If the RESET terminal goes LOW in STOP mode, it becomes that of a normal reset status.
                            // The following conditions should be met before a STOP instruction is executed and stop mode is entered:
                            // All interrupt-enable (IE) flags are reset.
                            // Input to P10-P13 is LOW for all.
                        }
                        System.out.println("0x10 instruction");
                        break;
                    case 0x1:
                        // cycles: 3, bytes: 3, LD r16, d16
                        ldR16d16(firstNibble, d16);
                        break;
                    case 0x2:
                        // cycles: 2, LD (DE), A
                        ldR16PtrA(firstNibble);
                        break;
                    case 0x3:
                        // cycles: 2, INC DE
                        regs.set(Reg.DE, regs.get(Reg.DE) + 1);
                        cycles++;
                        break;
                    case 0x4:
                        // cycles: 1, INC D
                        incR8(Reg.D);
                        break;
                    case 0x5:
                        // cycles: 1, DEC D
                        decR8(Reg.D);
                        break;
                    case 0x6:
                        // cycles: 2, LD D, d8
                        regs.set(Reg.D, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 1, rotate A left THROUGH CARRY FLAG
                        // the bit that is shifted out is put into carry, the carry is OR'd into LSB of A
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag((regs.get(Reg.A) & 0x80) >> 7 == 1);
                        regs.set(Reg.A, regs.get(Reg.A) << 1 | carryFlag);
                        break;
                    case 0x8:
                        // cycles: 3/2, JR s8
                        jumpRelative(true, s8);
                        break;
                    case 0x9:
                        // cycles: 2, ADD HL, DE
                        addHLr16(Reg.DE);
                        break;
                    case 0xA:
                        // cycles: 2, LD A, (DE)
                        ldAR16Ptr(firstNibble);
                        break;
                    case 0xB:
                        // DEC r16
                        regs.set(Reg.DE, regs.get(Reg.DE) - 1);
                        cycles++;
                        break;
                    case 0xC:
                        // cycles: 1, INC E
                        incR8(Reg.E);
                        break;
                    case 0xD:
                        // cycles: 1, DEC E
                        decR8(Reg.E);
                        break;
                    case 0xE:
                        // cycles: 2, LD E, d8
                        regs.set(Reg.E, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 1, rotate A right THROUGH CARRY FLAG
                        // the bit that is shifted out is put into carry, the carry is OR'd into MSB of A
                        regs.setCarryFlag((regs.get(Reg.A) & 0x1) == 1);
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.set(Reg.A, regs.get(Reg.A) >> 1 | (carryFlag << 7));
                        break;
                }
                break;
            case 0x2:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 3/2, JR NZ, s8
                        jumpRelative(regs.getZeroFlag() == 0, s8);
                        break;
                    case 0x1:
                        // cycles: 3, bytes: 3, LD r16, d16
                        ldR16d16(firstNibble, d16);
                        break;
                    case 0x2:
                        // cycles: 2, LD (HL+), A
                        ldR16PtrA(firstNibble);
                        break;
                    case 0x3:
                        // cycles: 2, INC HL
                        regs.set(Reg.HL, regs.get(Reg.HL) + 1);
                        cycles++;
                        break;
                    case 0x4:
                        // cycles: 1, INC H
                        incR8(Reg.H);
                        break;
                    case 0x5:
                        // cycles: 1, DEC H
                        decR8(Reg.H);
                        break;
                    case 0x6:
                        // cycles: 2, LD H, d8
                        regs.set(Reg.H, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 1, DAA
                        decimalAdjustAccumulator();
                        break;
                    case 0x8:
                        // cycles: 3/2, JR Z, s8
                        jumpRelative(regs.getZeroFlag() == 1, s8);
                        break;
                    case 0x9:
                        // cycles: 2, ADD HL, HL
                        addHLr16(Reg.HL);
                        break;
                    case 0xA:
                        // cycles: 2, LD A, (HL+)
                        ldAR16Ptr(firstNibble);
                        break;
                    case 0xB:
                        // DEC r16
                        regs.set(Reg.HL, regs.get(Reg.HL) - 1);
                        cycles++;
                        break;
                    case 0xC:
                        // cycles: 1, INC L
                        incR8(Reg.L);
                        break;
                    case 0xD:
                        // cycles: 1, DEC L
                        decR8(Reg.L);
                        break;
                    case 0xE:
                        // cycles: 2, LD L, d8
                        regs.set(Reg.L, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 1, Flip (one's complement) register A
                        regs.setSubtractionFlag(true);
                        regs.setHalfcarryFlag(true);
                        regs.set(Reg.A, regs.get(Reg.A) ^ 0xFF);
                        break;
                }
                break;
            case 0x3:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 3/2, JR NC, s8
                        jumpRelative(regs.getCarryFlag() == 0, s8);
                        break;
                    case 0x1:
                        // cycles: 3, bytes: 3, LD r16, d16
                        ldR16d16(firstNibble, d16);
                        break;
                    case 0x2:
                        // cycles: 2, LD (HL-), A
                        ldR16PtrA(firstNibble);
                        break;
                    case 0x3:
                        // cycles: 2, INC SP
                        regs.set(Reg.SP, regs.get(Reg.SP) + 1);
                        cycles++;
                        break;
                    case 0x4:
                        // cycles: 3, INC (HL)
                        incR8(Reg.HL);
                        break;
                    case 0x5:
                        // cycles: 1, DEC (HL)
                        decR8(Reg.HL);
                        break;
                    case 0x6:
                        // cycles: 3, LD (HL), d8
                        writeHLptr(d8);
                        regs.addPC(1);
                        cycles += 2;
                        break;
                    case 0x7:
                        // cycles: 1, SCF (set carry flag)
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag(true);
                        break;
                    case 0x8:
                        // cycles: 3/2, JR C, s8
                        jumpRelative(regs.getCarryFlag() == 1, s8);
                        break;
                    case 0x9:
                        // cycles: 2, ADD HL, SP
                        addHLr16(Reg.SP);
                        break;
                    case 0xA:
                        // cycles: 2, LD A, (HL-)
                        ldAR16Ptr(firstNibble);
                        break;
                    case 0xB:
                        // DEC r16
                        regs.set(Reg.SP, regs.get(Reg.SP) - 1);
                        cycles++;
                        break;
                    case 0xC:
                        // cycles: 1, INC A
                        incR8(Reg.A);
                        break;
                    case 0xD:
                        // cycles: 1, DEC A
                        decR8(Reg.A);
                        break;
                    case 0xE:
                        // cycles: 2, LD A, d8
                        regs.set(Reg.A, d8);
                        regs.addPC(1);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 1, Flip carry flag
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag(regs.getCarryFlag() == 0);
                        break;
                }
                break;
            case 0x4:
                // cycles: 1, load sourceRegValue into B or C
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle
                regs.set((secondNibble <= 0x7 ? Reg.B : Reg.C), sourceRegValue);
                break;
            case 0x5:
                // cycles: 1, load sourceRegValue into D or E
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle
                regs.set((secondNibble <= 0x7 ? Reg.D : Reg.E), sourceRegValue);
                break;
            case 0x6:
                // cycles: 1, load sourceRegValue into H or L
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle
                regs.set((secondNibble <= 0x7 ? Reg.H : Reg.L), sourceRegValue);
                break;
            case 0x7:
                if (secondNibble <= 0x5 || secondNibble == 0x7) {
                    cycles++; // using HL as pointer always takes an extra cycle
                    int memoryAddress = regs.get(Reg.HL);
                    memory.write(memoryAddress, sourceRegValue);
                } else if (secondNibble >= 0x8) {
                    regs.set(Reg.A, sourceRegValue);
                } else {
                    // TODO HALT instruction!
                    halted = true;
                    if (!interruptsEnabled && (memory.read(0xFF0F) & memory.read(0xFFFF)) != 0) {
                        if (haltBugCounter == 1) {
                            haltBugCounter = 0;
                            halted = false;
                        } else {
                            haltBugCounter++;
                        }
                    }
                    break;
                }
                break;
            case 0x8:
                // ADD/ADC to A
                // set sub bit
                regs.setSubtractionFlag(false);
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle

                if (secondNibble <= 0x7) {
                    // cycles: 1, ADD to A instruction
                    regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) + (sourceRegValue & 0xF)) > 0xF);
                    regs.setCarryFlag((regs.get(Reg.A) + sourceRegValue) > 0xFF);
                    regs.set(Reg.A, regs.get(Reg.A) + sourceRegValue);
                    regs.setZeroFlag(regs.get(Reg.A) == 0);
                } else {
                    // cycles: 1, ADC to A instruction
                    regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) + ((sourceRegValue & 0xF) + carryFlag) > 0xF));
                    regs.setCarryFlag(regs.get(Reg.A) + (sourceRegValue + carryFlag) > 0xFF);
                    regs.set(Reg.A, regs.get(Reg.A) + (sourceRegValue + carryFlag));
                    regs.setZeroFlag(regs.get(Reg.A) == 0);
                }
                break;
            case 0x9:
                // cycles: 1
                regs.setSubtractionFlag(true);
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle

                if (secondNibble <= 0x7) {
                    // SUB sourceRegValue from A
                    regs.setZeroFlag(regs.get(Reg.A) - sourceRegValue == 0);
                    regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < (sourceRegValue & 0xF)));
                    regs.setCarryFlag(regs.get(Reg.A) < sourceRegValue);
                    regs.set(Reg.A, regs.get(Reg.A) - sourceRegValue);
                } else {
                    // SBC (sub with carry) sourceRegValue from A
                    regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < ((sourceRegValue & 0xF) + carryFlag)));
                    regs.setCarryFlag(regs.get(Reg.A) < (sourceRegValue + carryFlag));
                    regs.set(Reg.A, regs.get(Reg.A) - (sourceRegValue + carryFlag));
                    regs.setZeroFlag(regs.get(Reg.A) == 0);
                }
                break;
            case 0xA:
                // cycles: 1
                regs.setSubtractionFlag(false);
                regs.setCarryFlag(false);
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle

                if (secondNibble <= 0x7) {
                    // AND
                    regs.set(Reg.A, regs.get(Reg.A) & sourceRegValue); // AND
                    regs.setHalfcarryFlag(true);
                } else {
                    // XOR
                    regs.set(Reg.A, regs.get(Reg.A) ^ sourceRegValue); // XOR
                    regs.setHalfcarryFlag(false);
                }

                regs.setZeroFlag(regs.get(Reg.A) == 0);
                break;
            case 0xB:
                // cycles: 1
                if (secondNibble <= 0x7) {
                    // OR
                    regs.setSubtractionFlag(false);
                    regs.setHalfcarryFlag(false);
                    regs.setCarryFlag(false);
                    regs.set(Reg.A, regs.get(Reg.A) | sourceRegValue);
                    regs.setZeroFlag(regs.get(Reg.A) == 0);
                } else {
                    // CP
                    regs.setSubtractionFlag(true);
                    regs.setZeroFlag(regs.get(Reg.A) - sourceRegValue == 0);
                    regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < (sourceRegValue & 0xF)));
                    regs.setCarryFlag(regs.get(Reg.A) < sourceRegValue);
                }
                break;
            case 0xC:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 5/2, RET NZ
                        condRet(regs.getZeroFlag() == 0);
                        break;
                    case 0x1:
                        // cycles: 3, POP BC
                        popR16(firstNibble);
                        break;
                    case 0x2:
                        //
                        condJump(regs.getZeroFlag() == 0, d16);
                        break;
                    case 0x3:
                        condJump(true, d16);
                        break;
                    case 0x4:
                        // cycles: 6/3, CALL NZ, a16
                        condCall(regs.getZeroFlag() == 0, d16);
                        break;
                    case 0x5:
                        // cycles: 4, PUSH BC
                        pushR16(firstNibble);
                        break;
                    case 0x6:
                        // cycles: 2, ADD A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(false);
                        regs.setZeroFlag(((regs.get(Reg.A) + d8) & 0xFF) == 0);
                        regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) + (d8 & 0xF)) > 0xF);
                        regs.setCarryFlag(regs.get(Reg.A) + d8 > 0xFF);
                        regs.set(Reg.A, regs.get(Reg.A) + d8);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 4, RST 0
                        callVec(instruction);
                        break;
                    case 0x8:
                        // cycles: 5/2, RET Z
                        condRet(regs.getZeroFlag() == 1);
                        break;
                    case 0x9:
                        // cycles: 4, RET
                        popPC();
                        cycles += 3;
                        break;
                    case 0xA:
                        // cycles: 4/3, JP Z, a16
                        condJump(regs.getZeroFlag() == 1, d16);
                        break;
                    case 0xB:
                        // Time for a big 16-bit instruction!
                        bigInstruction(d8);
                        break;
                    case 0xC:
                        // cycles: 6/3, CALL Z, d16
                        condCall(regs.getZeroFlag() == 1, d16);
                        break;
                    case 0xD:
                        // cycles: 6, CALL a16
                        condCall(true, d16);
                        break;
                    case 0xE:
                        // cycles: 2, ADC A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) + (d8 & 0xF) + carryFlag) > 0xF);
                        regs.setCarryFlag(((regs.get(Reg.A) & 0xFF) + ((d8) & 0xFF) + carryFlag) > 0xFF);
                        regs.set(Reg.A, regs.get(Reg.A) + (d8 + carryFlag));
                        regs.setZeroFlag(regs.get(Reg.A) == 0);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 4, RST 1
                        callVec(instruction);
                        break;
                }
                break;
            case 0xD:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 5/2, RET NC
                        condRet(regs.getCarryFlag() == 0);
                        break;
                    case 0x1:
                        // cycles: 3, POP DE
                        popR16(firstNibble);
                        break;
                    case 0x2:
                        // cycles: 4/3, JP NZ, a16
                        condJump(regs.getCarryFlag() == 0, d16);
                        break;
                    case 0x4:
                        // cycles: 4, CALL NC, a16
                        condCall(regs.getCarryFlag() == 0, d16);
                        break;
                    case 0x5:
                        // cycles: 4, PUSH DE
                        pushR16(firstNibble);
                        break;
                    case 0x6:
                        // cycles: 2, SUB A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(true);
                        regs.setZeroFlag(regs.get(Reg.A) - d8 == 0);
                        regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < (d8 & 0xF)));
                        regs.setCarryFlag(regs.get(Reg.A) < d8);
                        regs.set(Reg.A, regs.get(Reg.A) - d8);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 4, RST 2
                        callVec(instruction);
                        break;
                    case 0x8:
                        // cycles: 5/2, RET C
                        condRet(regs.getCarryFlag() == 1);
                        break;
                    case 0x9:
                        // cycles: 4, RETI (return from interrupt)
                        // Used when an interrupt-service routine finishes. The address for the return
                        // from the interrupt is loaded in the program counter PC. The master interrupt enable
                        // flag is returned to its pre-interrupt status.
                        //  ((The pre-interrupt status would reasonably have to be ON, else we wouldn't have called
                        //    the interrupt routine at all))
                        popPC();
                        interruptsEnabled = true;
                        cycles += 3;
                        break;
                    case 0xA:
                        // Cycles 4/3 JP C, a16
                        condJump(regs.getCarryFlag() == 1, d16);
                        break;
                    case 0xC:
                        // Cycles: 6/3 CALL C, a16
                        condCall(regs.getCarryFlag() == 1, d16);
                        break;
                    case 0xE:
                        // cycles: 2, SBC A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(true);
                        regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < ((d8 & 0xF) + carryFlag)));
                        regs.setCarryFlag(regs.get(Reg.A) < (d8 + carryFlag));
                        regs.set(Reg.A, regs.get(Reg.A) - (d8 + carryFlag));
                        regs.setZeroFlag(regs.get(Reg.A) == 0);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 4, RST 3
                        callVec(instruction);
                        break;
                }
                break;
            case 0xE:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 2, LD (a8), A
                        ldRamD8(firstNibble, d8);
                        break;
                    case 0x1:
                        popR16(firstNibble);
                        break;
                    case 0x2:
                        // cycles: 1, LD (C), A
                        ldRamC(firstNibble);
                        break;
                    case 0x5:
                        // cycles: 4, PUSH HL
                        pushR16(firstNibble);
                        break;
                    case 0x6:
                        // cycles: 2, AND A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(false);
                        regs.setCarryFlag(false);
                        regs.set(Reg.A, regs.get(Reg.A) & d8); // AND
                        regs.setHalfcarryFlag(true);
                        regs.setZeroFlag(regs.get(Reg.A) == 0);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 4, RST 4
                        callVec(instruction);
                        break;
                    case 0x8:
                        // cycles: 4, ADD SP, s8
                        // FIXME Not sure if correct AT ALL
                        regs.addPC(1);
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);

                        regs.setHalfcarryFlag(((regs.get(Reg.SP) & 0xF) + (s8 & 0xF)) > 0xF);
                        regs.setCarryFlag(((regs.get(Reg.SP) & 0xFF) + (s8 & 0xFF)) > 0xFF);
                        regs.set(Reg.SP, regs.get(Reg.SP) + s8);
                        cycles += 3;
                        break;
                    case 0x9:
                        // Cycles 1, JP HL
                        regs.set(Reg.PC, regs.get(Reg.HL));
                        break;
                    case 0xA:
                        // cycles: 3, LD (a16), A
                        ldRamD16(firstNibble, d16);
                        break;
                    case 0xE:
                        // cycles: 2, XOR A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(false);
                        regs.setCarryFlag(false);
                        regs.set(Reg.A, regs.get(Reg.A) ^ d8); // XOR
                        regs.setHalfcarryFlag(false);
                        regs.setZeroFlag(regs.get(Reg.A) == 0);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 4, RST 5
                        callVec(instruction);
                        break;
                }
                break;
            case 0xF:
                switch (secondNibble) {
                    case 0x0:
                        // cycles: 2, LD A, (d8)
                        ldRamD8(firstNibble, d8);
                        break;
                    case 0x1:
                        // cycles: 3, POP AF
                        popR16(firstNibble);
                        break;
                    case 0x2:
                        // cycles: 1, LD A, (C)
                        ldRamC(firstNibble);
                        break;
                    case 0x3:
                        // cycles: 1, DI (unset IME, disable interrupts)
                        this.interruptsEnabled = false;
                        break;
                    case 0x5:
                        // cycles: 4, PUSH AF
                        pushR16(firstNibble);
                        break;
                    case 0x6:
                        // cycles: 2, OR A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(false);
                        regs.setHalfcarryFlag(false);
                        regs.setCarryFlag(false);
                        regs.set(Reg.A, regs.get(Reg.A) | d8);
                        regs.setZeroFlag(regs.get(Reg.A) == 0);
                        cycles++;
                        break;
                    case 0x7:
                        // cycles: 4, RST 6
                        callVec(instruction);
                        break;
                    case 0x8:
                        // cycles 3: LD HL,SP+s8
                        regs.addPC(1);
                        regs.setZeroFlag(false);
                        regs.setSubtractionFlag(false);

                        regs.setHalfcarryFlag(((regs.get(Reg.SP) & 0xF) + (s8 & 0xF)) > 0xF);
                        regs.setCarryFlag((regs.get(Reg.SP) & 0xFF) + (s8 & 0xFF) > 0xFF);

                        regs.set(Reg.HL, regs.get(Reg.SP) + s8);
                        cycles += 2;
                        break;
                    case 0x9:
                        // LD SP, HL
                        regs.set(Reg.SP, regs.get(Reg.HL));
                        cycles++;
                        break;
                    case 0xA:
                        // cycles: 3, LD A, (a16)
                        ldRamD16(firstNibble, d16);
                        break;
                    case 0xB:
                        // cycles: 1, EI (set IME, enable interrupts)
                        this.interruptsEnabled = true;
                        break;
                    case 0xE:
                        // cycles: 2, CP A, d8
                        regs.addPC(1);
                        regs.setSubtractionFlag(true);
                        regs.setZeroFlag((regs.get(Reg.A) - d8) == 0);
                        regs.setHalfcarryFlag(((regs.get(Reg.A) & 0xF) < (d8 & 0xF)));
                        regs.setCarryFlag(regs.get(Reg.A) < d8);
                        cycles++;
                        break;
                    case 0xF:
                        // cycles: 4, RST 7
                        callVec(instruction);
                        break;
                }
                break;
        }

        int returnCycles = cycles;
        cycles = 0;
        return returnCycles;
    }

    /**
     * Many instructions use HL as a pointer to a memory address, and this is just for ergonomics.
     * @return the value from memory
     */
    private int readHLptr() {
        return memory.read(regs.get(Reg.HL));
    }

    /**
     * Many instructions use HL as a pointer to a memory address, and this is just for ergonomics.
     * @param value value to write
     */
    private void writeHLptr(int value) {
        memory.write(regs.get(Reg.HL), value);
    }

    /**
     * Add 1 to a given register.
     * @param reg
     */
    private void incR8(Reg reg) {
        // cycles: 1, INC r8
        int value = (reg == Reg.HL ? readHLptr() : regs.get(reg));

        regs.setZeroFlag(((value + 1) & 0xFF) == 0);
        regs.setSubtractionFlag(false);
        regs.setHalfcarryFlag(((value & 0xF) + 1) > 0xF);

        if (reg != Reg.HL) {
            regs.set(reg, value + 1);
        } else {
            writeHLptr(value + 1);
            cycles += 2;
        }
    }

    /**
     * Remove 1 from a given register.
     * @param reg
     */
    private void decR8(Reg reg) {
        // cycles: 1, DEC r8
        int value = (reg == Reg.HL ? readHLptr() : regs.get(reg));
        regs.setZeroFlag(((value - 1) & 0xFF) == 0);
        regs.setSubtractionFlag(true);
        regs.setHalfcarryFlag((value & 0xF) < 1);

        if (reg != Reg.HL) {
            regs.set(reg, value - 1);
        } else {
            writeHLptr(value - 1);
        }
    }

    /**
     * Adds the given register to the HL, and stores the result in HL.
     * @param reg
     */
    private void addHLr16(Reg reg) {
        // cycles: 2, ADD HL, BC
        regs.setSubtractionFlag(false);
        regs.setHalfcarryFlag(((regs.get(Reg.HL) & 0xFFF) + (regs.get(reg) & 0xFFF)) > 0xFFF);
        regs.setCarryFlag(regs.get(Reg.HL) + regs.get(reg) > 0xFFFF);
        regs.set(Reg.HL, regs.get(Reg.HL) + regs.get(reg));
        cycles++;
    }

    /**
     * Load the value from memory, pointed to by a 16-bit register, to A. Chooses register based on the first nibble of
     * the instruction.
     * @param firstNibble
     */
    private void ldAR16Ptr(int firstNibble) {
        switch (firstNibble) {
            case 0x0:
                // (BC)
                regs.set(Reg.A, memory.read(regs.get(Reg.BC)));
                break;
            case 0x1:
                // (DE)
                regs.set(Reg.A, memory.read(regs.get(Reg.DE)));
                break;
            case 0x2:
                // (HL+)
                regs.set(Reg.A, memory.read(regs.get(Reg.HL)));
                regs.set(Reg.HL, regs.get(Reg.HL) + 1);
                break;
            case 0x3:
                // (HL-)
                regs.set(Reg.A, memory.read(regs.get(Reg.HL)));
                regs.set(Reg.HL, regs.get(Reg.HL) - 1);
                break;
        }
        cycles++;
    }

    /**
     * Store the value in A to the memory address contained in a 16-bit register. Chooses register based on the first
     * nibble of the instruction. If the register is HL, it will also increase or decrease it post-write.
     * @param firstNibble
     */
    private void ldR16PtrA(int firstNibble) {
        switch (firstNibble) {
            case 0x0:
                // (BC)
                memory.write(regs.get(Reg.BC), regs.get(Reg.A));
                break;
            case 0x1:
                // (DE)
                memory.write(regs.get(Reg.DE), regs.get(Reg.A));
                break;
            case 0x2:
                // (HL+)
                memory.write(regs.get(Reg.HL), regs.get(Reg.A));
                regs.set(Reg.HL, regs.get(Reg.HL) + 1);
                break;
            case 0x3:
                // (HL-)
                memory.write(regs.get(Reg.HL), regs.get(Reg.A));
                regs.set(Reg.HL, regs.get(Reg.HL) - 1);
                break;
        }
        cycles++;
    }

    /**
     * Load immediate 16-bit number into a 16-bit register. Chooses register based on the first nibble of the
     * instruction.
     * @param firstNibble
     * @param d16
     */
    private void ldR16d16(int firstNibble, int d16) {
        // ld r16, d16
        switch (firstNibble) {
            case 0x0 -> regs.set(Reg.BC, d16);
            case 0x1 -> regs.set(Reg.DE, d16);
            case 0x2 -> regs.set(Reg.HL, d16);
            case 0x3 -> regs.set(Reg.SP, d16);
        }
        cycles += 2;
        regs.addPC(2);
    }

    /**
     * One of the trickier instructions. In short, it will format a to BCD (binary coded decimal) based on the current
     * flags.
     * @see <a href=https://rgbds.gbdev.io/docs/v0.9.1/gbz80.7#DAA>Rednex Game Boy Development System documentation: DAA instruction</a>
     */
    private void decimalAdjustAccumulator() {
        int adjustment = 0;

        if (regs.getSubtractionFlag() == 1) {
            if (regs.getHalfcarryFlag() == 1) {
                adjustment += 0x6;
            }
            if (regs.getCarryFlag() == 1) {
                adjustment += 0x60;
            }
            regs.set(Reg.A, regs.get(Reg.A) - adjustment);
        } else {
            if (regs.getHalfcarryFlag() == 1 || ((regs.get(Reg.A) & 0xF) > 0x9)) {
                adjustment += 0x6;
            }
            if (regs.getCarryFlag() == 1 || (regs.get(Reg.A) > 0x99)) {
                adjustment += 0x60;
                regs.setCarryFlag(true);
            } else {
                regs.setCarryFlag(false);
            }
            regs.set(Reg.A, regs.get(Reg.A) + adjustment);
        }
        regs.setZeroFlag(regs.get(Reg.A) == 0);
        regs.setHalfcarryFlag(false);
    }

    /**
     * Jumps relative to PC. This can be either unconditional (pass in cond = true) or conditional (write some condition
     * check when calling the method). It uses the immediate 8-bit number interpreted as a signed number to jump.
     * @param cond
     * @param s8 signed 8-bit immediate number
     */
    private void jumpRelative(boolean cond, int s8) {
        regs.addPC(1);
        if (cond) {
            regs.addPC(s8);
            cycles++;
        }
        cycles++;
    }

    /**
     * Jumps to immediate address. This can be either unconditional (pass in cond = true) or conditional
     * (write some condition check when calling the method). It uses the immediate 8-bit number interpreted as a
     * signed number to jump.
     * @param cond
     * @param d16 16-bit immediate address
     */
    private void condJump(boolean cond, int d16) {
        regs.addPC(2);
        if (cond) {
            regs.set(Reg.PC, d16);
            cycles++;
        }
        cycles += 2;
    }

    /**
     * Returns from a subroutine (pops PC from stack) depending on a condition.
     * @param cond
     */
    private void condRet(boolean cond) {
        if (cond) {
            popPC();
            cycles += 3;
        }
        cycles++;
    }

    /**
     * Calls a subroutine depending on condition passed in when calling. Pushes PC to the stack and jumps to the
     * immediate 16-bit address.
     * @param cond
     * @param d16 immediate 16-bit address
     */
    private void condCall(boolean cond, int d16) {
        regs.addPC(2);
        if (cond) {
            pushPC();
            regs.set(Reg.PC, d16);
            cycles += 4;
        }
        cycles++;
    }

    /**
     * Helper method. Chooses a 16-bit register label based on the first nibble of an instruction.
     * @param firstNibble
     * @return a register label
     */
    private Reg chooseR16(int firstNibble) {
        switch (firstNibble) {
            case 0xC:
                return Reg.BC;
            case 0xD:
                return Reg.DE;
            case 0xE:
                return Reg.HL;
            case 0xF:
                return Reg.AF;
            default:
                CuteLogger.log(Level.SEVERE, "Unknown reg 0x" + Integer.toHexString(firstNibble).toUpperCase());
                System.exit(-1);
        }

        return null; // unreachable
    }

    /**
     * Pops a 16-bit value from the stack and stores it in a 16-bit register.
     * @param firstNibble
     */
    private void popR16(int firstNibble) {
        Reg r16 = chooseR16(firstNibble); // choose register

        regs.set(r16, memory.read(regs.get(Reg.SP))); // set lower bits
        regs.incSP();
        regs.set(r16, (memory.read(regs.get(Reg.SP)) << 8) | regs.get(r16)); // set higher bits;
        regs.incSP();

        if (r16 == Reg.AF) regs.set(Reg.AF, regs.get(Reg.AF) & 0xFFF0);
        cycles += 2;
    }

    /**
     * Pushes a 16-bit register to the stack.
     * @param firstNibble
     */
    private void pushR16(int firstNibble) {
        Reg r16 = chooseR16(firstNibble);

        regs.decSP();
        memory.write(regs.get(Reg.SP), (regs.get(r16) & 0xFF00) >>> 8); // higher bits
        regs.decSP();
        memory.write(regs.get(Reg.SP), (regs.get(r16) & 0xFF));
        cycles += 3;
    }

    /**
     * Memory addresses 0x0, 0x08, 0x10, 0x18, ..., 0x38 are jump vectors. This method simply jumps according to the
     * instruction to one of these vectors.
     * @param instruction
     */
    private void callVec(int instruction) {
        pushPC();

        int instructionFirstNibble = (instruction & 0xF0) >> 4;
        boolean secondNibbleIsSeven = (instruction & 0x0F) == 0x7;
        int vec;

        switch (instructionFirstNibble) {
            case 0xC:
                vec = (secondNibbleIsSeven ? 0x00 : 0x08);
                break;
            case 0xD:
                vec = (secondNibbleIsSeven ? 0x10 : 0x18);
                break;
            case 0xE:
                vec = (secondNibbleIsSeven ? 0x20 : 0x28);
                break;
            case 0xF:
                vec = (secondNibbleIsSeven ? 0x30 : 0x38);
                break;
            default:
                CuteLogger.log(Level.SEVERE, "Somehow reached default switch case in callVec?");
                vec = 0xFF; // doesn't matter, we're crashing
                System.exit(-1);
        }

        regs.set(Reg.PC, vec);
        cycles += 3;
    }

    /**
     * Either stores the value in register A to a spot in high-memory (0xFF00...0xFFFF), or loads to A from that spot.
     * The spot is chosen by adding the immediate 8-bit number to 0xFF00.
     * @param firstNibble
     * @param d8
     */
    private void ldRamD8(int firstNibble, int d8) {
        int address = 0xFF00 | d8;
        regs.addPC(1);
        if (firstNibble == 0xE) {
            // one order
            memory.write(address, regs.get(Reg.A));
        } else {
            // other order
            regs.set(Reg.A, memory.read(address));
        }
        cycles += 2;
    }

    /**
     * This is very similar to {@link #ldRamD8}, but instead of using an immediate 8-bit number, uses the number in
     * register C.
     * @param firstNibble
     */
    private void ldRamC(int firstNibble) {
        int address = 0xFF00 | regs.get(Reg.C);
        if (firstNibble == 0xE) {
            memory.write(address, regs.get(Reg.A));
        } else {
            regs.set(Reg.A, memory.read(address));
        }
        cycles++;
    }

    /**
     * Loads value in register A to the immediate 16-bit address, or vice versa.
     * @param firstNibble
     * @param d16 immediate 16-bit address
     */
    private void ldRamD16(int firstNibble, int d16) {
        regs.addPC(2);
        if (firstNibble == 0xE) {
            memory.write(d16, regs.get(Reg.A));
        } else {
            regs.set(Reg.A, memory.read(d16));
        }

        cycles += 3;
    }

    /**
     * If the program counter reaches a 0xCB instruction, it means the following 8-bit number is to be interpreted as
     * an opcode. If we reach 0xCB, we pass in that opcode to this method. This is just to avoid another level of
     * nesting in the gigantic main switch statement, and to keep these secondary instructions together.
     * @param d8 immediate 8-bit instruction
     * @see <a href=https://www.google.com/>Meganesu CPU Instructions (scroll down)</a>
     */
    private void bigInstruction(int d8) {
        int firstNibble = (d8 & 0xF0) >> 4;
        int secondNibble = (d8 & 0x0F);
        cycles++;
        regs.addPC(1);

        final int carryFlag = regs.getCarryFlag();

        Reg sourceReg = Registers.getSourceRegByNibble(secondNibble);
        int sourceRegValue;
        if (sourceReg == Reg.HL) {
            // if HL is the source register, we should use the value that it is pointing to in memory
            cycles++; // using HL as pointer always takes an extra cycle
            sourceRegValue = memory.read(regs.get(Reg.HL));
        } else {
            sourceRegValue = regs.get(sourceReg);
        }
        switch (firstNibble) {
            case 0x0:
                regs.setSubtractionFlag(false);
                regs.setHalfcarryFlag(false);
                if (sourceReg == Reg.HL) cycles++; // using HL as pointer always takes an extra cycle

                if (secondNibble <= 0x7 && secondNibble != 0x6) {
                    // cycles: 2, RLC r8
                    regs.setCarryFlag((sourceRegValue & 0x80) == 0x80);
                    regs.set(sourceReg, (sourceRegValue << 1) | ((sourceRegValue & 0x80) >> 7));
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else if (secondNibble == 0x6) {
                    // cycles: 4, RLC (HL)
                    regs.setCarryFlag((sourceRegValue & 0x80) == 0x80);
                    memory.write(regs.get(Reg.HL), (sourceRegValue << 1) | ((sourceRegValue & 0x80) >> 7));
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                    cycles += 2;
                } else if (secondNibble != 0xE) {
                    // cycles: 2, RRC r8
                    regs.setCarryFlag((sourceRegValue & 0x1) == 0x1);
                    regs.set(sourceReg, (sourceRegValue >> 1) | ((sourceRegValue & 0x1) << 7));
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else {
                    // cycles: 4, RRC (HL)
                    regs.setCarryFlag((sourceRegValue & 0x1) == 0x1);
                    memory.write(regs.get(Reg.HL), (sourceRegValue >> 1) | ((sourceRegValue & 0x1) << 7));
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                    cycles += 2;
                }
                break;
            case 0x1:
                regs.setSubtractionFlag(false);
                regs.setHalfcarryFlag(false);

                if (secondNibble <= 0x7 && secondNibble != 0x6) {
                    // cycles: 2, RL r8
                    regs.setCarryFlag((sourceRegValue & (1 << 7)) != 0);
                    regs.set(sourceReg, (sourceRegValue << 1) | carryFlag);
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else if (secondNibble == 0x6) {
                    // cycles: 4, RL (HL)
                    regs.setCarryFlag((sourceRegValue & (1 << 7)) != 0);
                    memory.write(regs.get(sourceReg), (sourceRegValue << 1) | carryFlag);
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                    cycles += 2;
                } else if (secondNibble != 0xE) {
                    // cycles: 2, RR r8
                    regs.setCarryFlag((sourceRegValue & (1)) != 0);
                    regs.set(sourceReg, (sourceRegValue >> 1) | (carryFlag << 7));
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else {
                    // cycles: 4, RR (HL)
                    regs.setCarryFlag((sourceRegValue & (1)) != 0);
                    memory.write(regs.get(sourceReg), (sourceRegValue >> 1) | (carryFlag << 7));
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                    cycles += 2;
                }
                break;
            case 0x2:
                regs.setSubtractionFlag(false);
                regs.setHalfcarryFlag(false);

                if (secondNibble <= 0x7 && secondNibble != 0x6) {
                    // cycles: 2, SLA r8
                    regs.set(sourceReg, sourceRegValue << 1);
                    regs.setCarryFlag((sourceRegValue & 0x80) == 0x80);
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else if (secondNibble == 0x6) {
                    // cycles: 4, SLA (HL)
                    memory.write(regs.get(sourceReg), (sourceRegValue << 1));
                    regs.setCarryFlag((sourceRegValue & 0x80) == 0x80);
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                } else if (secondNibble != 0xE) {
                    // cycles: 2, SLA r8
                    regs.set(sourceReg, sourceRegValue >> 1 | (sourceRegValue & 0x80));
                    regs.setCarryFlag((sourceRegValue & 0x1) == 0x1);
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else {
                    // cycles: 4, SLA (HL)
                    memory.write(regs.get(sourceReg), sourceRegValue >> 1 | (sourceRegValue & 0x80));
                    regs.setCarryFlag((sourceRegValue & 0x1) == 0x1);
                    regs.setZeroFlag(memory.read(regs.get(Reg.HL)) == 0x0);
                }
                break;
            case 0x3:
                regs.setSubtractionFlag(false);
                regs.setHalfcarryFlag(false);

                final int swapped = (sourceRegValue & 0xF0) >> 4 | (sourceRegValue & 0xF) << 4;

                if (secondNibble <= 7 && secondNibble != 0x6) {
                    // cycles: 2, SWAP r8 (swap the nibbles)
                    regs.setCarryFlag(false);
                    regs.set(sourceReg, swapped);
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else if (secondNibble == 0x6) {
                    // cycles: 4, SWAP (HL)
                    regs.setCarryFlag(false);
                    memory.write(regs.get(sourceReg), swapped);
                    regs.setZeroFlag(memory.read(regs.get(sourceReg)) == 0x0);
                    cycles += 2;
                } else if (secondNibble != 0xE) {
                    // cycles: 2, SRL r8
                    regs.setCarryFlag((sourceRegValue & 0x1) == 0x1);
                    regs.set(sourceReg, sourceRegValue >> 1);
                    regs.setZeroFlag(regs.get(sourceReg) == 0x0);
                } else {
                    // cycles: 4, SRL (HL)
                    regs.setCarryFlag((memory.read(regs.get(sourceReg)) & 0x1) == 0x1);
                    memory.write(regs.get(Reg.HL), memory.read(regs.get(Reg.HL)) >> 1);
                    regs.setZeroFlag(memory.read(regs.get(sourceReg)) == 0x0);
                    cycles += 2;
                }
                break;
            case 0x4:
                setZeroToInverseBit((secondNibble <= 0x7 ? 0 : 1), sourceReg, sourceRegValue);
                break;
            case 0x5:
                setZeroToInverseBit((secondNibble <= 0x7 ? 2 : 3), sourceReg, sourceRegValue);
                break;
            case 0x6:
                setZeroToInverseBit((secondNibble <= 0x7 ? 4 : 5), sourceReg, sourceRegValue);
                break;
            case 0x7:
                setZeroToInverseBit((secondNibble <= 0x7 ? 6 : 7), sourceReg, sourceRegValue);
                break;
            case 0x8:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue & 0xFE);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xFE));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue & 0xFD);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xFD));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0x9:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue & 0xFB);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xFB));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue & 0xF7);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xF7));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xA:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue & 0xEF);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xEF));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue & 0xDF);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xDF));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xB:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue & 0xBF);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0xBF));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue & 0x7F);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue & 0x7F));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xC:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue | 0x1);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x1));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue | 0x2);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x2));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xD:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue | 0x4);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x4));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue | 0x8);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x8));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xE:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue | 0x10);
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x10));
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue | 0x20);
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x20));
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
            case 0xF:
                if (secondNibble <= 7 && secondNibble != 0x6) {
                    regs.set(sourceReg, sourceRegValue | 0x40); // 0100 0000
                } else if (secondNibble == 0x6) {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x40)); // 0100 0000
                } else if (secondNibble != 0xE) {
                    regs.set(sourceReg, sourceRegValue | 0x80); // 1000 0000
                } else {
                    memory.write(regs.get(sourceReg), (sourceRegValue | 0x80)); // 1000 0000
                }
                if (sourceReg == Reg.HL) cycles++;
                break;
        }
    }

    private void setZeroToInverseBit(int bit, Reg sourceReg, int sourceRegValue) {
        regs.setSubtractionFlag(false);
        regs.setHalfcarryFlag(true);
        regs.setZeroFlag((sourceRegValue & (1 << bit)) == 0x0);
        if (sourceReg == Reg.HL) cycles++;
    }

    /**
     * There are two built-in timers in the Game Boy, the DIV timer and the TIMA timer. DIV is constantly ticking and
     * overflowing, whereas TIMA has some settings for the program to control. In short, this method will calculate the
     * increment of the timers based on the cycles the CPU ran.
     * @param cycles
     * @see <a href=https://gbdev.io/pandocs/Timer_and_Divider_Registers.html>Pan Docs - Timer and Divider Registers</a>
     */
    public void updateTimers(int cycles) {

        // FF04 incs by 16384 every SECOND
        // divide that by dots
        double dotsPerSecond = 4.1943 * 1_000_000;
        // 4 dots per m cycle
        double cyclesPerSecond = dotsPerSecond / 4;
        double divIncrement = (16384 * (cycles / cyclesPerSecond)); // maybe this will floor to 0, TODO

        memory.incDivTimer(divIncrement);

        memory.incTimaCycles(cycles);
    }
}
