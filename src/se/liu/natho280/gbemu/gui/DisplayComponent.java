package se.liu.natho280.gbemu.gui;

import se.liu.natho280.gbemu.ppu.Display;
import se.liu.natho280.gbemu.ppu.Pixel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Handles drawing the display in the GUI. The Game Boy only has four colors, and as such
 * a lot is left up to us when deciding how to present it. Listens to the Display class for
 * finished screen draws.
 */
public class DisplayComponent extends JComponent implements DisplayListener {
    private int scalingFactor = 4;
    private Display display;

    private static final int GB_LCD_WIDTH = 160;
    private static final int GB_LCD_HEIGHT = 144;
    private final Dimension preferredSize = new Dimension(GB_LCD_WIDTH * scalingFactor, GB_LCD_HEIGHT * scalingFactor);

    private final BufferedImage image = new BufferedImage(GB_LCD_WIDTH, GB_LCD_HEIGHT, BufferedImage.TYPE_INT_RGB);

    public DisplayComponent(Display display) {
        this.display = display;
        this.display.addDisplayListener(this);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);

        final Graphics2D g2d = (Graphics2D) g;

	Pixel[][] displayCopy = display.getCopy();

        for (int y = 0; y < display.getHeight(); y++) {
            for (int x = 0; x < display.getWidth(); x++) {
                image.setRGB(x, y, Pixel.pixelToRGB(displayCopy[y][x]));
            }
        }

        BufferedImage scaledImage = scale(image, preferredSize.width, preferredSize.height);
        g2d.drawImage(scaledImage, 0, 0, null);
    }

    public static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, source.getType());
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // Or other interpolation
        g2d.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return scaledImage;
    }

    @Override
    public void displayChanged() {
        this.repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return preferredSize;
    }

    /**
     * Upon resizing the main window, this method is used to recalculate the scale factor of the game graphics
     * @param scalingFactor
     */
    public void setScalingFactor(int scalingFactor) {
        this.scalingFactor = scalingFactor;
        this.preferredSize.setSize(GB_LCD_WIDTH * scalingFactor, GB_LCD_HEIGHT * scalingFactor);
    }
}
