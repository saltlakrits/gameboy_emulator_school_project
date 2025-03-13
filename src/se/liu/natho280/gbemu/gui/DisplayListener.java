package se.liu.natho280.gbemu.gui;

import se.liu.natho280.gbemu.ppu.Display;

/**
 * A display listener is notified when the display changes. Likely when a full frame has been drawn.
 * @see Display
 * @see DisplayComponent
 */
public interface DisplayListener {
    void displayChanged();
}
