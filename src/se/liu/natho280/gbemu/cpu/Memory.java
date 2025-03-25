package se.liu.natho280.gbemu.cpu;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.serialization.SerializableMBC;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;
import se.liu.natho280.gbemu.debugger.MBCListener;
import se.liu.natho280.gbemu.debugger.MemoryListener;
import se.liu.natho280.gbemu.ppu.OAM;
import se.liu.natho280.gbemu.rom.ROM;

import javax.swing.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * <p>This is the <a href=https://gbdev.io/pandocs/Memory_Map.html>memory</a> of the Game Boy. Input is stored here, as
 * well as all the regions of the memory. VRAM should not be accessible during certain parts of the PPU's cycle, but
 * that is not yet taken into account in this code. However, for future-proofing, the PPU can read the memory
 * with its own method, and the CPU uses the normal read() and write() methods for reads and writes that are
 * conditionally blocked.</p>
 *
 * <p>There are many hardware registers where reads and writes are intercepted and doing something different
 * than simply reading and writing to the array.</p>
 */
public class Memory implements Serializable
{
    // 0x0000 - 0x3FFF is the ROM (bank 00)
    // 0x4000 - 7FFF is the ROM (bank 1-NN)
    private ROM rom = null;

    // each location in memory is a byte
    // addresses: 0x0000 through 0xFFFF
    // ROM addresses removed from memory
    private static final int ADDRESSES_IN_MEMORY = 0x10_000;
    private static final int ADDRESSES_IN_ROM = 0x8_000;

    private static final int RAM_START_ADDRESS = 0xA000;
    private static final int RAM_END_ADDRESS = 0xBFFF;

    private static final int ROM_START_ADDRESS = 0x0000;
    private static final int ROM_END_ADDRESS = 0x7FFF;

    private static final int VRAM_START_ADDRESS = 0x8000;
    private static final int VRAM_END_ADDRESS = 0x9FFF;

    private UnsignedByte[] memory = new UnsignedByte[ADDRESSES_IN_MEMORY - ADDRESSES_IN_ROM];
    private static final int BYTE_OVERFLOW_MODULO = 256;
    private static final int BYTE_MAX = 0xFF;
    private int buttonByte = 0xFF; // default value -> no inputs -> all bits are set
    private int dpadByte = 0xFF; // default value -> no inputs -> all bits are set

    // lock all read/write for 640 dots upon initiating a DMA transfer
    private static final int DMA_LOCK_DOTS = 640;
    private int dmaTransferLock = 0; // See Pan Docs - DMA Transfer
    private boolean vramLocked = false;

    private double divTimer = 0.0;
    private int flooredDivTimer = 0;
    private int tima = 0;
    private int timaCycles = 0;

    private volatile boolean validROM = false;

    private transient List<MemoryListener> memoryListeners = new ArrayList<>();

    private transient List<MBCListener> mbcListeners = new ArrayList<>();

    public Memory() {
        for (int i = 0; i < memory.length; i++) {
            memory[i] = new UnsignedByte(0);
        }
    }

    public Memory(String romPath) {
        // create (& probably load) ROM with romPath string

        // catch exception
        loadROM(romPath);

        // initialize memory
        for (int i = 0; i < memory.length; i++) {
            memory[i] = new UnsignedByte(0);
        }
    }

    private void loadROM(String romPath) {
        try {
            this.rom = new ROM(romPath);
            this.validROM = true;
            for (MBCListener listener : mbcListeners) {
                this.rom.addMBCListener(listener);
            }
            CuteLogger.log(Level.INFO, "Successfully loaded ROM.");
        } catch (IllegalStateException e) {
            this.validROM = false;
            CuteLogger.log(Level.WARNING, "Failed to load ROM. Error: " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() + "\n\nTip: Make sure the file you are trying to load is a Game Boy ROM-file!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * For ROM switching
     * @param newROM
     */
    public void reInitializeMemory(String newROM) {
        for (int i = 0; i < memory.length; i++) {
            unconditionalWrite(i + ADDRESSES_IN_ROM, 0);
        }

        resetDivTimer();
        tima = 0;
        timaCycles = 0;

        loadROM(newROM);
    }

    /**
     * For ROM resetting
     */
    public void reInitializeMemory() {
        this.rom.reset();

        if (mbcListeners != null) {
            for (MBCListener l : mbcListeners) {
                this.rom.addMBCListener(l);
            }
        }
    }

    public boolean hasValidROM() {
        return validROM;
    }

    public void restoreState(SerializationWrapper serializationWrapper) {
        Memory wrapperMemory = serializationWrapper.getMemory();
        if (wrapperMemory == null) throw new IllegalStateException("Deserialization failed");
        this.memory = wrapperMemory.memory;
        this.buttonByte = wrapperMemory.buttonByte;
        this.dpadByte = wrapperMemory.dpadByte;
        this.dmaTransferLock = wrapperMemory.dmaTransferLock;
        this.vramLocked = wrapperMemory.vramLocked;
        this.divTimer = wrapperMemory.divTimer;
        this.flooredDivTimer = wrapperMemory.flooredDivTimer;
        this.tima = wrapperMemory.tima;
        this.timaCycles = wrapperMemory.timaCycles;
        this.validROM = wrapperMemory.validROM;

        // If a ROM isn't loaded already, we need an empty object to unpack the state into
        if (this.rom == null) {
            this.rom = new ROM();
        }
        this.rom.restoreState(serializationWrapper, mbcListeners);
    }

    public SerializableMBC getSerializableMBC() {
        return (this.rom != null) ? this.rom.getSerializableMBC() : null;
    }

    public ROM getROM() {
        return this.rom.copy();
    }

    public void resetDivTimer() {
        divTimer = 0.0;
        flooredDivTimer = 0;
    }

    public void incDivTimer(double value) {
        divTimer = (divTimer + value) % BYTE_OVERFLOW_MODULO;
        flooredDivTimer = ((int)divTimer) % BYTE_OVERFLOW_MODULO;

        memory[0xFF04 - 0x8000].set(flooredDivTimer);
    }

    public void incTimaCycles(int cycles) {
        int timerControl = unconditionalRead(0xFF07); // TAC register
        if ((timerControl & (1 << 2)) == 0) return; // if bit 3 is off, TIMA isn't incremented
        final int firstTwoBits = 3;

        int mod = 0;
        // the following switch statement is many, many times slower as a Map than as a simple switch statement, as such it is left
        // as a switch
        // the first 2 bits of the timer control register determines how often TIMA increments.
        switch (timerControl & firstTwoBits) {
            case 0:
                mod = 256;
                break;
            case 1:
                mod = 4;
                break;
            case 2:
                mod = 16;
                break;
            case 3:
                mod = 64;
                break;
        }

        timaCycles += cycles;
        if (timaCycles >= mod) {
            // inc timer...
            tima += 1;
            // reset timaCycles
            timaCycles -= mod;
        }
        if (tima > BYTE_MAX) {
            // if tima should overflow, we overflow it and set it to TMA (Timer Modulo)
//            tima = memory[0xFF06].get();
            tima = unconditionalRead(0xFF06); //  TMA read
            // raise TIMA interrupt -> Bit 2 of FF0F
//            memory[0xFF0F].set(memory[0xFF0F].get() | (1 << 2));
            // set interrupt bit
            unconditionalWrite(0xFF0F, unconditionalRead(0xFF0F) | (1 << 2)); // interrupt (this will happen quite a bit)
//            memory[0xFF0F - 0x8000].set(unconditionalRead(0xFF0F) | (1 << 2));
        }
//        memory[0xFF05].set(tima);
//        unconditionalWrite(0xFF05, tima);
        memory[0xFF05 - ADDRESSES_IN_ROM].set(tima);
    }

    public void subDmaTransferLock(int dots) {
        if (dmaTransferLock > 0) dmaTransferLock -= dots;
        if (dmaTransferLock < 0) dmaTransferLock = 0;
    }

    public void lockVram() {
        vramLocked = true;
    }

    public void unlockVram() {
        vramLocked = false;
    }

    public void write(int address, int value) {
        int subtractedAddress = address - ADDRESSES_IN_ROM;

        if (address <= ROM_END_ADDRESS) {
            rom.write(address, value);
        } else if (address >= RAM_START_ADDRESS && address <= RAM_END_ADDRESS) {
            rom.write(address, value); // writing to RAM a separate method?
        } else if (address == 0xFF04) {
            resetDivTimer();
            //if (address == 0xFF01) System.out.print((char)value); // print serial port writes
        } else if (address == 0xFF46) {
            // copy 0x9F objects from WRAM to the object memory area
            // takes 160 cycles = 640 dots
            // value will in this case be start address / 0x100
            for (int i = 0; i <= 0x9F; i++) {
                // For each spot in memory between 0xXX00-0xXX9F, copy it to the object memory
                // which is 0xFE00-0xFE9F
//                memory[0xFE00 + i] = memory[(value * 0x100) | i];
//                unconditionalWrite(0xFE00 + i, unconditionalRead(value * 0x100 + i));
                int newVal = memory[(value * 0x100 + i - ADDRESSES_IN_ROM)].get();
                memory[0xFE00 - ADDRESSES_IN_ROM + i].set(newVal);
            }
            // lock all read/write for 640 dots
            dmaTransferLock = DMA_LOCK_DOTS;
        } else if (address <= VRAM_END_ADDRESS) {
//            if (!vramLocked) memory[address] = new UnsignedByte(value); // FIXME
//            if (!vramLocked) unconditionalWrite(address, value);
            if (!vramLocked) memory[subtractedAddress].set(value);
        } else {
//            if (dmaTransferLock == 0) memory[address] = new UnsignedByte(value); // FIXME
//            if (dmaTransferLock == 0) unconditionalWrite(address, value);
            if (dmaTransferLock == 0) memory[subtractedAddress].set(value);
            //memory[subtractedAddress].set(value);
        }

        for (MemoryListener memoryListener : memoryListeners) {
            memoryListener.memoryChanged(address);
        }
    }

    public int read(int address) {
        if (address >= 0x0 && address <= ROM_END_ADDRESS) {
            return rom.get(address);
        } else if (address >= RAM_START_ADDRESS && address <= RAM_END_ADDRESS) {
            // reading from RAM must be redirected through MBC
            return rom.get(address);
        } else if (address == 0xFF00) {
            // input register!
            // depending on bit 4 and bit 5, we should return different input registers
            int input = (unconditionalRead(0xFF00) & 0x30) >> 4;
            if ((input & 0x1) == 0) {
                return dpadByte & 0xF;
            } else if ((input & 0x2) == 0) {
                return buttonByte & 0xF;
            } else {
                return 0xF;
            }
        } else if (address >= VRAM_START_ADDRESS && address <= VRAM_END_ADDRESS) {
//            if (!vramLocked) return memory[address].get();
//            else return 0xFF;
            if (!vramLocked) return unconditionalRead(address);
            else return 0xFF; // default return value if VRAM is locked, which isn't implemented yet
        }
        // maybe need to add other regions as well
        return unconditionalRead(address); // never gets here
    }

    /**
     * For the PPU, and various other classes to use. The PPU & the others are never
     * locked out of reading.
     * @param address
     * @return
     */
    public int unconditionalRead(int address) {
        if (address >= 0x0 && address <= ROM_END_ADDRESS) {
            if (this.rom == null) return 0;
            return rom.get(address);
        }
        return memory[address - ADDRESSES_IN_ROM].get();
    }

    /**
     * For the PPU, and various other classes to use. The PPU & the others are never
     * locked out of writing.
     * @param address
     * @param value
     */
    public void unconditionalWrite(int address, int value) {
        if (address >= ROM_START_ADDRESS && address <= ROM_END_ADDRESS) {
            rom.write(address, value);
            return;
        }

        memory[address - ADDRESSES_IN_ROM].set(value);

        for (MemoryListener memoryListener : memoryListeners) {
            memoryListener.memoryChanged(address);
        }
    }

    public OAM[] getOAMs() {
        OAM[] oams = new OAM[40];
        for (int i = 0; i < (oams.length * 4); i += 4) {
            int y = unconditionalRead(0xFE00 + i);
            int x = unconditionalRead(0xFE00 + (i + 1));
            int index = unconditionalRead(0xFE00 + (i + 2));
            int flags = unconditionalRead(0xFE00 + (i + 3));
            OAM newOAM = new OAM(y, x, index, flags);
            oams[i / 4] = newOAM;
        }
        return oams;
    }

    /**
     * Releasing a button means un-setting (1'ing, unintuitively) the bit in the appropriate byte.
     * Also calls {@link Memory#setInputInterrupt}.
     * @param b a button to release
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public void releaseButton(GameButton b) {

        switch (b) {
            case A, B, START, SELECT -> buttonByte |= (1 << GameButton.buttonToBit(b));
            case UP, DOWN, LEFT, RIGHT -> dpadByte |= (1 << GameButton.buttonToBit(b));
        }

        switch (b) {
            case START, SELECT, A, B -> setInputInterrupt(false, false);
            case DOWN, UP, LEFT, RIGHT -> setInputInterrupt(true, false);
        }
    }

    /**
     * Setting a button means 0'ing (yes, 0'ing) the bit in the appropriate byte.
     * Also calls {@link Memory#setInputInterrupt}.
     * @param b a button to press
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public void setButton(GameButton b) {

        switch (b) {
            case A, B, START, SELECT -> buttonByte &= ((1 << GameButton.buttonToBit(b)) ^ 0xFF);
            case UP, DOWN, LEFT, RIGHT -> dpadByte &= ((1 << GameButton.buttonToBit(b)) ^ 0xFF);
        }

        switch (b) {
            case START, SELECT, A, B -> setInputInterrupt(false, true);
            case DOWN, UP, LEFT, RIGHT -> setInputInterrupt(true, true);
        }
    }

    /**
     * If certain conditions apply, we should request an input {@link Interrupt} when buttons are pressed.
     * Handled in {@link CPU#checkInterrupts}.
     * @param dpad
     * @param setToHigh
     * @see <a href=https://gbdev.io/pandocs/Interrupt_Sources.html#int-60--joypad-interrupt>Pan Docs - Joypad Interrupt</a>
     */
    private void setInputInterrupt(boolean dpad, boolean setToHigh) {
        int interruptFlags = unconditionalRead(0xFFFF);
        boolean inputInterruptsEnabled = (interruptFlags & (1 << 4)) != 0;
        int newInterruptByte = unconditionalRead(0xFF0F);

        if (!inputInterruptsEnabled) {
            // unset the interrupt if they aren't enabled, and do an early return
            newInterruptByte &= interruptFlags;
            unconditionalWrite(0xFF0F, newInterruptByte);
            return;
        }
        // if dpad -> bit 4, else action buttons -> bit 5
        int bit = (dpad ? 4 : 5);
        // mask out the old interrupt bit by AND'ing with inverse bits of 0x10
        newInterruptByte &= ((1 << 4) ^ 0xFF);

        // if button is pressed, raise interrupt, if released, remove interrupt signal
        if ((unconditionalRead(0xFF00) & (1 << bit)) == 0) {
            unconditionalWrite(0xFF0F, newInterruptByte | ((setToHigh ? 1 : 0) << 4));
        }
    }

    public void addMemoryListener(MemoryListener memoryListener) {
        this.memoryListeners.add(memoryListener);
    }

    public void addMBCListener(MBCListener l) {
        this.mbcListeners.add(l);
        if (this.rom != null) rom.addMBCListener(l);
    }

    /**
     * Calls method on the ROM, if it isn't null. Used by classes higher in the hierarchy.
     */
    public void saveRAM() {
        if (this.rom != null) this.rom.saveRAM();
    }
}
