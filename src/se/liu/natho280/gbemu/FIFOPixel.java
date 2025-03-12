package se.liu.natho280.gbemu;

/**
 * A se.liu.natho280.GbEmu.FIFOPixel is used in {@link PPU#mode3}, where {@link FIFOQueue}s of FIFOPixels are created, pixels are popped off the queues,
 * and finally pushed to the LCD. This is simply an abstraction for a slimmed-down way to look at an {@link OAM}, with
 * a lot of information discarded.
 */
public class FIFOPixel {
    int colorValue = 0;
    boolean palette = false;
    boolean bgPrio = false;

    public FIFOPixel(int colorValue, boolean palette, boolean bgPrio) {
        this.colorValue = colorValue;
        this.palette = palette;
        this.bgPrio = bgPrio;
    }
}
