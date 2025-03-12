/**
 * The original game boy (DMG, Pocket & Light) only had 4 colors; really, these were 4 shades of green (or grey)
 * with one of them being "blank", or the normal background color.
 * As such, for our screen, we internally only need to set the color according to the Game Boy capabilities
 * and in our actual frontend we can interpret and present these colors any way we prefer.
 */
public enum Pixel {
    BLACK, MEDIUM, LIGHT, WHITE
}
