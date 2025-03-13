package se.liu.natho280.gbemu.ppu;

/**
 * A FIFOPixel is used in {@link PPU#mode3}, where {@link FIFOQueue}s of FIFOPixels are created, pixels are popped off the queues,
 * and finally pushed to the LCD. This is simply an abstraction for a slimmed-down way to look at an {@link OAM}, with
 * a lot of information discarded.
 */
public class FIFOPixel {
    /**
     * The color value of the FIFOPixel.
     */
    public int colorValue;
    /**
     * The corresponding (of two possible) palettes.
     */
    public boolean palette;
    /**
     * Whether background color values > 0 has priority over this pixel when drawing on the
     * screen.
     */
    public boolean bgPrio;

    public FIFOPixel(int colorValue, boolean palette, boolean bgPrio) {
        this.colorValue = colorValue;
        this.palette = palette;
        this.bgPrio = bgPrio;
    }
}
