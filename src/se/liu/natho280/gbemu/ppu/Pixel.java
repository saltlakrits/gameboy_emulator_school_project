package se.liu.natho280.gbemu.ppu;

/**
 * The original game boy (DMG, Pocket & Light) only had 4 colors; really, these were 4 shades of green (or grey)
 * with one of them being "blank", or the normal background color.
 * As such, for our screen, we internally only need to set the color according to the Game Boy capabilities
 * and in our actual frontend we can interpret and present these colors any way we prefer.
 */
public enum Pixel {
    BLACK, MEDIUM, LIGHT, WHITE;

    /**
     * Converts a Pixel to a suitable RGB value to show to the user
     * @param pixel
     * @return
     */
    public static int pixelToRGB(Pixel pixel) {
        return switch (pixel) {
            case Pixel.WHITE -> 0xe6e6e6;
            case Pixel.LIGHT -> 0xcccccc;
            case Pixel.MEDIUM -> 0xa6a6a6;
            case Pixel.BLACK -> 0x666666;
        };
    }
}
