import java.util.ArrayList;
import java.util.List;

/**
 * <p>There are 6 16-bit registers in the Game Boy, but four of them can also be accessed as if they were 8-bit
 * registers (AF, BC, DE, HL -> A, F, B, C, D, E, H, L). A is the accumulator, F is the flag register, and the rest
 * are simply registers.</p>
 *
 * <p>HL is often used as a pointer register, but this is not any kind of hard rule. SP is the
 * stack pointer, and PC is the program counter.</p
 * @see <a href=https://gbdev.io/pandocs/CPU_Registers_and_Flags.html>Pan Docs - CPU Registers and Flags</a>
 */
public class Registers {
    // 6 of them, 16 bit

    // AF -> Accumulator & Flags
    // BC -> normal regs, can fetch B (top byte), C (bottom byte), or BC (whole 16 bits)
    // DE -> same
    // HL -> same
    // SP -> stack pointer, can only get whole 16-bit number
    // PC -> program counter, can only get whole 16-bit number

    private final UnsignedShort[] registers = new UnsignedShort[6];
    private final List<RegisterListener> registerListeners = new ArrayList<>();

    public Registers() {
        for (int i = 0; i < registers.length; i++) {
            // SP starts at 0xFFFE (end of high ram), rest of the registers are initialized to 0
            registers[i] = new UnsignedShort((i == 4 ? 0xFFFE : 0));
        }
    }

    public void addRegisterListener(RegisterListener l) {
        registerListeners.add(l);
    }

    /**
     * Increase stack pointer. Shrinks the stack, as it grows backwards in memory.
     */
    public void incSP() {
        set(Reg.SP, get(Reg.SP) + 1);
    }

    /**
     * Decrease stack pointer. Grows the stack, as it grows backward in memory.
     */
    public void decSP() {
        set(Reg.SP, get(Reg.SP) - 1);
    }

    /**
     * Adds a number to the program counter. Useful for skipping ahead in instructions when immediate numbers are read,
     * or when relative jumps are made.
     * @param value
     */
    public void addPC(int value) {
        set(Reg.PC, get(Reg.PC) + value);
    }

    /**
     * Set a register to a value.
     * @param reg register to set, by label
     * @param value
     */
    public void set(Reg reg, int value) {
        // set whole register, or top/bottom 8 bits of one.
        switch (reg) {
            case A -> registers[0].set(((value & 0xFF) << 8) | (registers[0].get() & 0xFF));
            case F -> registers[0].set((registers[0].get() & 0xFF00) | (value & 0xFF)); // could be value & 0xF0 since bottom 4 bits should always be 0
            case AF -> registers[0].set(value & 0xFFFF);
            case B -> registers[1].set(((value & 0xFF) << 8) | (registers[1].get() & 0xFF));
            case C -> registers[1].set((registers[1].get() & 0xFF00) | (value & 0xFF));
            case BC -> registers[1].set(value & 0xFFFF);
            case D -> registers[2].set(((value & 0xFF) << 8) | (registers[2].get() & 0xFF));
            case E -> registers[2].set(registers[2].get() & 0xFF00 | (value & 0xFF));
            case DE -> registers[2].set(value & 0xFFFF);
            case H -> registers[3].set(((value & 0xFF) << 8) | (registers[3].get() & 0xFF));
            case L -> registers[3].set((registers[3].get() & 0xFF00) | (value & 0xFF));
            case HL -> registers[3].set(value & 0xFFFF);
            case SP -> registers[4].set(value & 0xFFFF);
            case PC -> registers[5].set(value & 0xFFFF);
        }

        for (RegisterListener l : registerListeners) {
            l.registerUpdated(reg);
        }
    }

    /**
     * Get value from a register by label.
     * @param reg register to get value from, by label
     * @return value stored in register
     */
    public int get(Reg reg) {
        // get whole register, or top/bottom 8 bits of one.
        return switch (reg) {
            case A -> (registers[0].get() & 0xFF00) >> 8;
            case F -> registers[0].get() & 0xFF;
            case AF -> registers[0].get() & 0xFFFF;
            case B -> (registers[1].get() & 0xFF00) >> 8;
            case C -> registers[1].get() & 0xFF;
            case BC -> registers[1].get() & 0xFFFF;
            case D -> (registers[2].get() & 0xFF00) >> 8;
            case E -> registers[2].get() & 0xFF;
            case DE -> registers[2].get() & 0xFFFF;
            case H -> (registers[3].get() & 0xFF00) >> 8;
            case L -> registers[3].get() & 0xFF;
            case HL -> registers[3].get() & 0xFFFF;
            case SP -> registers[4].get() & 0xFFFF;
            case PC -> registers[5].get() & 0xFFFF;
        };
    }

    /**
     * Many instructions have the second nibble signify a source register for the instruction,
     * e.g. 0x4X means load register X into register B (0x40-0x47) or C (0x48-0x4F).
     * This method lets us quickly decode that second nibble into which register it signifies and grab the value in it,
     * simplifying the big, core instruction-decoding switch statement.
     * @param nibble The (usually second) nibble from the instruction, to be decoded into a register
     * @return the register the nibble corresponds to
     */
    public static Reg getSourceRegByNibble(int nibble) {
        // FIXME temp name! perhaps inaccurate in some situations!
        // NOTE: This originally returned a register (enum value), but it was rewritten to return the register
        // value instead, and moved from the Emu class to the Registers class.

        // the pattern simply repeats for values 0x8 - 0xF, so we can just subtract 0x8 and get the same result.
        //if (nibble >= 0x8 && nibble <= 0xF) {
        //    return getSourceRegByNibble(nibble - 0x8);
        //}

        // pattern repeats from 7-F, so we can just modulo the nibble
        return switch (nibble % 8) {
            case 0x0 -> Reg.B;
            case 0x1 -> Reg.C;
            case 0x2 -> Reg.D;
            case 0x3 -> Reg.E;
            case 0x4 -> Reg.H;
            case 0x5 -> Reg.L;
            case 0x6 -> Reg.HL;
            case 0x7 -> Reg.A;
            default -> throw new IllegalArgumentException("Invalid nibble!");
        };
    }

    /**
     * Get the zero flag from the F, flag, register.
     * @return flag value
     */
    public int getZeroFlag() {
        return (get(Reg.F) & (1 << 7)) >> 7;
    }

    /**
     * Get the subtraction flag from the F, flag, register.
     * @return flag value
     */
    public int getSubtractionFlag() {
        return (get(Reg.F) & (1 << 6)) >> 6;
    }

    /**
     * Get the half-carry flag from the F, flag, register.
     * @return flag value
     */
    public int getHalfcarryFlag() {
        return (get(Reg.F) & (1 << 5)) >> 5;
    }

    /**
     * Get the carry flag from the F, flag, register.
     * @return flag value
     */
    public int getCarryFlag() {
        return (get(Reg.F) & (1 << 4)) >> 4;
    }

    private void setFlag(int bit, boolean bool) {
        // we either OR in the relevant bit if we should set it,
        // or mask it away if we should unset it
        if (bool) {
            set(Reg.F, get(Reg.F) | (1 << bit));
        } else {
            // the bitmask is a shifted bit XOR'd for the *inverse* 8-bit bitfield.
            set(Reg.F, get(Reg.F) & ((1 << bit) ^ 0xFF));
        }
    }

    /**
     * Set the zero flag to on (true) or off (false)
     * @param bool
     */
    public void setZeroFlag(boolean bool) {
        // set zero bit to off
        setFlag(7, bool);
    }

    /**
     * Set the subtraction flag to on (true) or off (false)
     * @param bool
     */
    public void setSubtractionFlag(boolean bool) {
        setFlag(6, bool);
    }

    /**
     * Set the half-carry flag to on (true) or off (false)
     * @param bool
     */
    public void setHalfcarryFlag(boolean bool) {
        setFlag(5, bool);
    }

    /**
     * Set the carry flag to on (true) or off (false)
     * @param bool
     */
    public void setCarryFlag(boolean bool) {
        setFlag(4, bool);
    }
}
