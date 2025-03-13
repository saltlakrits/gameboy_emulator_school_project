package se.liu.natho280.gbemu.ppu;

import se.liu.natho280.gbemu.gui.DisplayComponent;
import se.liu.natho280.gbemu.gui.DisplayListener;

import java.util.ArrayList;
import java.util.List;

// Tile data is stored in VRAM between 0x8000-0x9FFF, each tile taking 16 bytes (max 384 tiles)
// each tile is 8x8 pixels

// The Game Boy contains two 32×32 tile maps in VRAM at the memory areas
// $9800-$9BFF and $9C00-$9FFF. Any of these maps can be used to display the Background or the Window.

/**
 * A "screen", for putting pixels into. During vblank, when a full frame is completed, we draw this 2d array to the
 * interface.
 * @see PPU#mode3
 * @see DisplayComponent#paintComponent
 * @see <a href=https://gbdev.io/pandocs/Rendering.html>Pan Docs - Rendering</a>
 */
public class Display {
    private final Pixel[][] display = new Pixel[144][160];
    private final List<DisplayListener> displayListeners = new ArrayList<>();
    private Pixel[][] displayCopy = new Pixel[144][160];

    public Display() {
        for (int y = 0; y < getHeight(); y++) {
            for (int x = 0; x < getWidth(); x++) {
                display[y][x] = Pixel.BLACK;
            }
        }
        displayCopy = getDisplayCopy();
    }

    public void putPixel(int x, int y, Pixel pixel) {
        display[y][x] = pixel;
        if ( y == 143 && x == 159) {
            displayCopy = getDisplayCopy();
            notifyDisplayListeners();
        }
    }

    public Pixel[][] getCopy() {
        return displayCopy;
    }

    private Pixel[][] getDisplayCopy() {

        Pixel[][] copy = new Pixel[getHeight()][getWidth()];
        for (int y = 0; y < display.length; y++) {
            copy[y] = display[y].clone();
        }
        return copy;

    }

    public int getHeight() {
        return display.length;
    }

    public int getWidth() {
        return display[0].length;
    }

    public void addDisplayListener(DisplayListener displayListener) {
        displayListeners.add(displayListener);
    }

    public void notifyDisplayListeners() {
        for (DisplayListener displayListener : displayListeners) {
            displayListener.displayChanged();
        }
    }
}
