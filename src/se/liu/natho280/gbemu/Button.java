package se.liu.natho280.gbemu;

/**
 * Labels for buttons, used in {@link EmuViewer} and {@link Memory#setButton}, {@link Memory#releaseButton}
 * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
 */
public enum Button {
    LEFT, RIGHT, UP, DOWN, A, B, START, SELECT;

    /**
     * Match button with bit-index used in 0xFF00, the input matrix.
     * @param button
     * @return
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public static int buttonToBit(Button button) {
        return switch (button) {
            case RIGHT, A -> 0;
            case LEFT, B -> 1;
            case UP, SELECT -> 2;
            case DOWN, START -> 3;
        };
    }
}
