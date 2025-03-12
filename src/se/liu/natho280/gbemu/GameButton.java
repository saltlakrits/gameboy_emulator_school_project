package se.liu.natho280.gbemu;

/**
 * Labels for buttons, used in {@link EmuViewer} and {@link Memory#setButton}, {@link Memory#releaseButton}
 * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
 */
public enum GameButton
{
    LEFT, RIGHT, UP, DOWN, A, B, START, SELECT;

    /**
     * Match button with bit-index used in 0xFF00, the input matrix.
     * @param gameButton
     * @return
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public static int buttonToBit(GameButton gameButton) {
        return switch (gameButton) {
            case RIGHT, A -> 0;
            case LEFT, B -> 1;
            case UP, SELECT -> 2;
            case DOWN, START -> 3;
        };
    }
}
