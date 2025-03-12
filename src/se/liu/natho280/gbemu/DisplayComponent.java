package se.liu.natho280.gbemu;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class DisplayComponent extends JComponent implements DisplayListener {
    private static final int RESIZE_FACTOR = 4;
    private Display display = null;
    private Pixel[][] displayCopy = null;
    private final Dimension PREFERRED_SIZE = new Dimension(160 * RESIZE_FACTOR, 144 * RESIZE_FACTOR);

    private final BufferedImage image = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);

    public DisplayComponent(Display display) {
        this.display = display;
        this.display.addDisplayListener(this);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);

        final Graphics2D g2d = (Graphics2D) g;

        displayCopy = display.getCopy();

        for (int y = 0; y < display.getHeight(); y++) {
            for (int x = 0; x < display.getWidth(); x++) {
                switch (displayCopy[y][x]) {
                    case Pixel.WHITE -> image.setRGB(x, y, 0xe6e6e6);
                    case Pixel.LIGHT -> image.setRGB(x, y, 0xcccccc);
                    case Pixel.MEDIUM -> image.setRGB(x, y, 0xa6a6a6);
                    case Pixel.BLACK -> image.setRGB(x, y, 0x666666);
                }
            }
        }

        BufferedImage scaledImage = scale(image, PREFERRED_SIZE.width, PREFERRED_SIZE.height);
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
        return PREFERRED_SIZE;
    }
}
