package se.liu.natho280.gbemu;

/**
 * A display listener is notified when the display changes. Likely when a full frame has been drawn.
 * @see Display
 * @see DisplayComponent
 */
public interface DisplayListener {
    void displayChanged();
}
