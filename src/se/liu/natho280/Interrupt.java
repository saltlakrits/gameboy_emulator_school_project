/**
 * Labels for the interrupts. Interrupts are set in various places ({@link Memory#setInputInterrupt},
 * {@link PPU#handleStatRegister}, {@link PPU#vblank}, et cetera) and handled at the start of every CPU cycle
 * ({@link CPU#checkInterrupts}) -- if they should be.
 */
public enum Interrupt {
    VBLANK, STAT, TIMER, SERIAL, JOYPAD;

    /**
     * Matches an interrupt to the corresponding bit-index of 0xFFFF and 0xFF0F
     * @param interrupt an interrupt label
     * @return a bit-index
     * @see <a href=https://gbdev.io/pandocs/Interrupts.html>Pan Docs - Interrupts</a>
     */
    public static int matchInterruptBit(Interrupt interrupt) {
        return switch (interrupt) {
            case VBLANK -> 0;
            case STAT -> 1;
            case TIMER -> 2;
            case SERIAL -> 3;
            case JOYPAD -> 4;
        };
    }
}
