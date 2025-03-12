package se.liu.natho280.gbemu;

/**
 * Labels for the {@link Registers}. Just for handling them easily.
 * @see <a href=https://gbdev.io/pandocs/CPU_Registers_and_Flags.html>Pan Docs - se.liu.natho280.GbEmu.CPU se.liu.natho280.GbEmu.Registers and Flags</a>
 */
public enum Reg {
    A, F, AF, B, C, BC, D, E, DE, H, L, HL, SP, PC;

    public static String toString(Reg reg) {
        return switch (reg) {
            case A -> "A";
            case F -> "F";
            case B -> "B";
            case C -> "C";
            case D -> "D";
            case E -> "E";
            case H -> "H";
            case L -> "L";
            case AF -> "AF";
            case BC -> "BC";
            case DE -> "DE";
            case HL -> "(HL)";
            case SP -> "SP";
            case PC -> "PC";
        };
    }

    public static void main(String[] args) {
        System.out.println(Reg.toString(Reg.HL));
    }
}
