package se.liu.natho280.gbemu;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>The se.liu.natho280.GbEmu.PPU <a href=https://gbdev.io/pandocs/Graphics.html>Pan Docs - Graphics (Picture Processing Unit)</a>
 * is the hardware that manages putting graphics on the screen. Easily the most complex part of the emulator as of
 * this writing. Most likely, the way you expect it to work makes some sort
 * of logical sense, whereas the way it actually works does not.</p>
 *
 * <p>No short description of how this really works is sufficient.
 * However, as for a description of what this class does, we create an instance of it in se.liu.natho280.GbEmu.Main, and
 * we call the different methods that approximate different sections of a line (and frame)
 * draw for each ~16 milliseconds. This ensures that the frame draws remain in some sort of sync with the se.liu.natho280.GbEmu.CPU,
 * and will display roughly 60 frames per second (should be around 59.7, matching the original Game Boy).</p>
 *
 * <p>Despite the result we present being rather good-looking, the process of putting it on the screen is very
 * inaccurate to how the Game Boy is supposed to function. The reason we did not make something entirely abstract
 * is because approximating how it works, but taking some liberties, makes it a lot easier dealing with timing.</p>
 *
 * <p>That said, some games WILL look wrong.</p>
 *
 * @see <a href=https://gbdev.io/pandocs/Rendering.html>Pan Docs - Rendering</a>
 * @see <a href=https://hacktix.github.io/GBEDG/ppu/>Hacktix' Game Boy Emulator Development Guide - se.liu.natho280.GbEmu.PPU</a>
 */
public class PPU {
    private Display display = null;
    private Memory memory = null;
    private final StatLine statLine = new StatLine();
    private int flags = 0;
    private final List<OAM> lineOAMs = new ArrayList<>();
    private int lastScanline = 0;

    // these lacked performance
    // NOTE: they probably didn't, we just ran the se.liu.natho280.GbEmu.PPU way too frequently (redrawing the same line many times per frame)
    // it is unlikely they were actually a bottleneck. that said, these FIFOs ended up being more convenient for the
    // sprite FIFO when the logic behind filling it was rewritten, so it worked out in the end.
    // ArrayDeque<se.liu.natho280.GbEmu.FIFOPixel> bgFifo = new ArrayDeque<>();
    // ArrayDeque<se.liu.natho280.GbEmu.FIFOPixel> sprFifo = new ArrayDeque<>();

    private FIFOQueue bgFifo = new FIFOQueue();
    private FIFOQueue sprFifo = new FIFOQueue();

    public PPU(Display display, Memory memory) {
        this.display = display;
        this.memory = memory;
    }

    public Pixel getPixel(int color) {
        return switch (color) {
            case 0x0 -> Pixel.WHITE;
            case 0x1 -> Pixel.LIGHT;
            case 0x2 -> Pixel.MEDIUM;
            case 0x3 -> Pixel.BLACK;
            default -> throw new IllegalArgumentException("Unknown color: " + color);
        };
    }

    /**
     * Checks whether any flags are set.
     * @return true if any, else false
     */
    public boolean anyFlag() {
        return (flags != 0);
    }

    /**
     * Check if flag at a certain bit is set or not.
     * @param bit bit-index of the flag
     * @return true if set, else false
     */
    public boolean getFlag(int bit) {
        return ((flags & (1 << bit)) != 0);
    }

    /**
     * Set a bit-flag by index.
     * @param bit bit-index
     */
    public void setFlag(int bit) {
        flags |= (1 << bit);
    }

    /**
     * Reset flags, un-setting all of them.
     */
    public void resetFlags() {
        flags = 0;
    }

    /**
     * Conveniently set the bit signifying whether an interrupt is requested or not.
     * Current understanding is that for example the vblank bit should be reset after the vblank period ends, though
     * this may be a misunderstanding. It doesn't, however, make much sense to give the program the option to call
     * a vblank interrupt when it's no longer relevant.
     * @param interrupt the interrupt you want to set
     * @param bool true/false for 1/0
     */
    private void setInterruptBit(Interrupt interrupt, boolean bool) {
        int interruptBit = Interrupt.matchInterruptBit(interrupt);

        // check if interrupt is enabled before setting
        int interruptFlags = memory.unconditionalRead(0xFFFF);
//        System.out.println(interruptFlags & (1 << 1));
        if ((interruptFlags & (1 << interruptBit)) == 0) {
            return;
        }

        // mask away the current flag
        int oldFlags = memory.unconditionalRead(0xFF0F) & ((1 << interruptBit) ^ 0xFF);
        // OR in the interruptBit we want
        memory.unconditionalWrite(0xFF0F, oldFlags | ((bool ? 1 : 0) << interruptBit));
    }

    /**
     * STAT se.liu.natho280.GbEmu.Interrupt depends on which flags (in 0xFF41) the program has set. This is a convenience function
     * that keeps all the logic together in one place.
     * @see StatLine
     * @see <a href=https://gbdev.io/pandocs/STAT.html#ff41--stat-lcd-status>Pan Docs - FF41 — STAT: LCD status</a>
     * @see <a href=https://gbdev.io/pandocs/Interrupt_Sources.html#int-48--stat-interrupt>Pan Docs - INT $48 — STAT interrupt</a>
     */
    public void handleStatRegister(StatReg label) {
        // FF41 = STAT register
        int statReg = memory.unconditionalRead(0xFF41);
        //System.out.println(Integer.toBinaryString(statReg) + ", got: " + label);
        // FF44 = LY
        // FF45 = LYC (LY Compare, this is set by program!)

        boolean statInterrupt = false;
        switch (label) {
            case ZERO: // hblank mode
                memory.unconditionalWrite(0xFF41, (statReg & ((3 ^ 0xFF))) | 0x0);

                // add "signal" to STAT-line and grab whether it goes from low to high
                statInterrupt = statLine.addSignal((statReg & (1 << 3)) != 0);
                // iff it went high, set interrupt
                setInterruptBit(Interrupt.STAT, statInterrupt);
                break;
            case ONE: // vblank mode
                memory.unconditionalWrite(0xFF41, (statReg & ((3 ^ 0xFF))) | 0x1);

                // add "signal" to STAT-line and grab whether it goes from low to high
                statInterrupt = statLine.addSignal((statReg & (1 << 4)) != 0);
                // iff it went high, set interrupt
                setInterruptBit(Interrupt.STAT, statInterrupt);
                break;
            case TWO: // se.liu.natho280.GbEmu.OAM scan mode
                memory.unconditionalWrite(0xFF41, (statReg & ((3 ^ 0xFF))) | 0x2);

                // add "signal" to STAT-line and grab whether it goes from low to high
                statInterrupt = statLine.addSignal((statReg & (1 << 5)) != 0);
                // iff it went high, set interrupt
                setInterruptBit(Interrupt.STAT, statInterrupt);

                break;
            case THREE: // drawing scanline mode
                memory.unconditionalWrite(0xFF41, (statReg & ((3 ^ 0xFF))) | 0x3);
                statLine.reset();
                break;
            case LYC:
                // Check if LY==LYC and set registers and flags appropriately
                boolean lycEqLy = memory.unconditionalRead(0xFF44) == memory.unconditionalRead(0xFF45);

                // add "signal" to STAT-line and grab whether it goes from low to high
                statInterrupt = statLine.addSignal(lycEqLy);
                // iff it went high, set interrupt
                setInterruptBit(Interrupt.STAT, statInterrupt);


                statReg &= ((1 << 2) ^ 0xFF); // mask away the last LYC bit
                statReg |= (lycEqLy ? 1 : 0) << 2; // OR in the new bit
                memory.unconditionalWrite(0xFF41, statReg); // we also update the bit of the STAT register
                break;
        }
    }

    public void oamScan(int ly) {
        // unset vblank interrupt
        setInterruptBit(Interrupt.VBLANK, false);

        handleStatRegister(StatReg.TWO);

        // se.liu.natho280.GbEmu.OAM Scan for y-coord matching objects, se.liu.natho280.GbEmu.OAM is between 0xFE00 to 0xFE9F // 40 dots per scanline!!
        OAM[] OAMs = memory.getOAMs();
        int lcdc = memory.unconditionalRead(0xFF40);
        // bit 2 of LCD Control flags determine whether sprite height is 8 or 16 pixels.
        int spriteHeight = (lcdc & (1 << 2)) == 0 ? 8 : 16;

        lineOAMs.clear();

        // if bit 0 is 0, we don't draw objects at all!
        if ((lcdc & 0x1) == 1) {
//            if (ly == 128) System.out.println("YEAH");
            // for a given scanline, we only draw a maximum of 10 sprites. filter the rest out.
            for (OAM obj : OAMs) {
                if (ly >= obj.getY() - 16 && obj.getY() - 16 + spriteHeight > ly) {
//                    System.out.println("this is some real ghetto debugging");
                    lineOAMs.add(obj);
                }

                // we only want 10 sprites on a single line, so if we have 10, we break the loop.
                if (lineOAMs.size() == 10) break;
            }
        }

        setFlag(0);
    }

    /**
     * Draws a scanline, from left to right. This is a very involved process, and likely the most complicated in the
     * whole emulator. The se.liu.natho280.GbEmu.OAM section shouldn't (? FIXME) be able to change during a screen draw, so it should
     * only be read once and passed in or all scanlines.
     * @param ly the current y-coordinate
     */
    public void mode3(int ly) {
        // this is to avoid redrawing the same scanline
        if (ly + 1 == lastScanline) return;

        handleStatRegister(StatReg.THREE);

        int lcdc = memory.unconditionalRead(0xFF40);
        int wy = memory.unconditionalRead(0xFF4A); // window y start
        int wx = memory.unconditionalRead(0xFF4B) - 7; // window x start + 7 (for some reason)
//        System.out.println(wx + " " + wy);
//         if (ly == 0) System.out.println("LY = " + ly + " LCDC: " + Integer.toBinaryString(lcdc));
//        if (ly == 143) System.out.println("LY = " + ly + " LCDC: " + Integer.toBinaryString(lcdc) + "\n");
        int scy = memory.unconditionalRead(0xFF42); // scroll y
        int scx = memory.unconditionalRead(0xFF43); // scroll x
        //System.out.println("SCY: " + scy + " SCX: " + scx);
        int palette = memory.unconditionalRead(0xFF47); // 4-color palette
        int sprPal1 = memory.unconditionalRead(0xFF48); // OBP0
        int sprPal2 = memory.unconditionalRead(0xFF49); // OBP1
        // bit 2 of LCD Control flags determine whether sprite height is 8 or 16 pixels.
//        int spriteHeight = ((lcdc & 0x4) >> 2 == 0) ? 8 : 16;
        int spriteHeight = (lcdc & (1 << 2)) == 0 ? 8 : 16;
        // when y >= wy && x >= (wx - 7), we should draw window pixels.

        // for each pixel in the row, determine whether to draw a sprite, the window, or the background

        int x = 0;
        // se.liu.natho280.GbEmu.FIFOQueue stage
        while (x < 160) {
            // get tile
            // -- we determine which background/window tile to fetch pixels from. we default to 0x9800 tilemap.
            int tilemapAdr = 0x9800;

            // some things can change this.
            // if LCDC.3 and fetcherX-coord is not inside window, use 0x9c00.
            // NOTE: very hazy about the "fetcherX-coord inside the window" part. But I do know that the window is as
            // big as the background, you're just not supposed to overlay the entire window over the bg.
            if ((lcdc & (1 << 3)) !=  0 && (x < wx || ly < wy || (lcdc & (1 << 6)) == 0)) {
                tilemapAdr = 0x9C00;
            }
            if ((lcdc & (1 << 6)) !=  0 && x >= wx && ly >= wy && (lcdc & (1 << 5)) != 0) {
                tilemapAdr = 0x9C00;
            }

            int fetcherY, fetcherX; // coords for the tile fetcher

            if (x == wx && ly >= wy && (lcdc & (1 << 5)) != 0) {
                bgFifo.clear();
            }

            boolean inWindow = ly >= wy && x >= wx && (lcdc & (1 << 5)) != 0;
            // if we are inside window, use window x coordinate to fetch tile
            // else use mathy fetcherX
            // now determine fetcher coords!
            if (inWindow) {
                if (ly == 143) System.out.println("woo");

                // use window coords to get window tile!
                // WE FLUSH THE BG PIXELS AND GET WINDOW PIXELS
                fetcherY = ly - wy; // i think this is wrong, should be WINDOW_LINE_COUNTER
                fetcherX = (x - wx) / 8;
                //System.out.println("Tilemap: " + Integer.toHexString(tilemapAdr).toUpperCase() + ", y: " + ly + ", fetcherY: " + fetcherY + "\nx: " + x + ", fetcherX: " + fetcherX);
            } else {
                // use mathy coords to get bg tile! note that it is moved by scrolling
                // NOTE that we still need to drop (scx % 8) pixels at the start of the scanline!!!
                fetcherY = (ly + scy) & 0xFF;
                fetcherX = ((x + scx) / 8) & 0x1F;
            }


            // check bg fifo: if size == 0, build it
            if (bgFifo.isEmpty()) {
                buildBgFifo(lcdc, tilemapAdr, fetcherY, fetcherX);
//                buildBgFifoTheRevenge(tilemapAdr, fetcherY, fetcherX);
                // drop (scx % 8) pixels iff we are at the start of the scanline
                if (x == 0 && !inWindow) {
                    for (int i = 0; i < (scx % 8); i++) {
                        bgFifo.pop();
                    }
                }
            }

            // add sprites to sprite se.liu.natho280.GbEmu.FIFOQueue
            buildSprFifo(spriteHeight, x, ly);

            // now choose a pixel to push
            // if LCDC.7 is 0, the screen is off, and should be whiter than white! completely blank! no pixel colors!

            FIFOPixel candidate = null;

//            System.out.println("Sprites in queue!");
            candidate = sprFifo.pop();
            // If sprite color is 0, the pixel is transparent and should not be drawn
            boolean bgInstead = candidate.colorValue == 0;
            // Priority: 0 = No, 1 = BG and Window colors 1–3 are drawn over this OBJ
            bgInstead |= candidate.bgPrio && bgFifo.getFirst().colorValue > 0;
            // if we are to push a pixel from sprFifo, but LCDC.1 is 0, it should be background!
            bgInstead |= ((lcdc & (1 << 1)) == 0);
            if (bgInstead) {
                // discard and draw bg instead
                //System.out.println("Discarded sprite!");
                candidate = bgFifo.pop();
                candidate.colorValue = matchPixelColor(candidate.colorValue, palette);
            } else {
                // we ARE drawing a sprite: fix palette
                //System.out.println("Drawing a sprite!");
                bgFifo.pop();
                candidate.colorValue = matchPixelColor(candidate.colorValue, candidate.palette ? sprPal2 : sprPal1);
                //if ((lcdc & (1 << 0)) == 0) candidate.colorValue = 0x00; // blank it, bg/window is off
            }

            //if (candidate.colorValue != 0)
            //System.out.println("Pushing a pixel! colorInt: " + Integer.toHexString(candidate.colorValue) + " | colorMap.get(): " + colorMap.get(candidate.colorValue));
            display.putPixel(x, ly, getPixel(candidate.colorValue));
            // inc x

            x++;
        }
        bgFifo.clear();
        sprFifo.clear();
        lastScanline++;
        if (ly == 143) setFlag(1);
    }

    public void hblank() {
        handleStatRegister(StatReg.ZERO);
        setFlag(2);
        handleStatRegister(StatReg.LYC);
    }

    public void vblank() {
        setInterruptBit(Interrupt.VBLANK, true);
        handleStatRegister(StatReg.ONE);
        lastScanline = 0;
    }

    private int matchPixelColor(int pixelColor, int palette) {
        return switch(pixelColor) {
            // pixelColor:       0b11
            // palette: 0b11 11 11 11
            case 0x0 -> (palette & (3));
            case 0x1 -> (palette & (3 << 2)) >> 2;
            case 0x2 -> (palette & (3 << 4)) >> 4;
            case 0x3 -> (palette & (3 << 6)) >> 6;
            default -> throw new IllegalArgumentException();
        };
    }

    private void buildBgFifo(int lcdc, int tilemapAdr, int fetcherY, int fetcherX) {
        int bgPixelRow = getBgOrWindowTileRow(lcdc, tilemapAdr, fetcherY, fetcherX);
        // TODO this may be slightly inefficient, doubt it matters in practice
        // add the row of bgPixels to the bgFifo
        for (int i = 0; i < 8; i++) {
            int pxColor =  bgPixelRow & (0x3 << (14 - (i * 2)));
            pxColor >>= (14 - (i * 2));
            // note that only the first parameter matters for background/window tiles!
            bgFifo.add(new FIFOPixel(pxColor, false, false));
        }
    }

    private void buildSprFifoOld(int spriteHeight, int x, int ly) {
        for (OAM sprite : lineOAMs) {
            //String spriteString = sprite.getX() + ", " + sprite.getY();
            //System.out.println(spriteString);
            if (sprFifo.size() == 8) break;

            if (x >= (sprite.getX() - 8)) {
                int spriteTileAddr = sprite.getTileAddress();
                // 1-8 of the pixels of the sprite

                // y coord? i.e. which row of the tile -- can be flipped!
                // if ly == 4 and sprite.getY() == 16: 4 - 16 + 16 == 4
                // if ly == 16 and sprite.getY() == 32: 16 - 32 + 16 == 0
                // if ly == 20 and sprite.getY() == 32: 20 - 32 + 16 == 4
                // etc
                int spriteRow = ly - (sprite.getY() - 16);

                // if the sprite should be flipped vertically, we want to count rows from the bottom.
                // i.e. if we had row == 3 with spriteHeight 8, we now want row 4 (fourth from the bottom instead of
                // fourth fromt the top)
                if (sprite.getFlagYFlip()) spriteRow = (spriteHeight - 1) - spriteRow;
                // y:100 - (sprite.getY():104 - spriteHeight:8) == row:4
                // y:101 - (sprite.getY():104 - spriteHeight:8) == row:5
                // yFlip: 7 - 5 = 2
                // ...next row: 7 - 6 = 1
                // ...last row: 7 - 7 = 0
                // as such the last row we fetch is 0. so flipped!
                spriteRow *= 2; // there are two bytes per actual row, and we use this to 'skip ahead' in memory.
                // as such we double it to get to the appropriate row.

                int firstSpriteByte = memory.unconditionalRead(spriteTileAddr + spriteRow);
                int secondSpriteByte = memory.unconditionalRead(spriteTileAddr + spriteRow + 1);
                // x coord? can be flipped!
                int sprX = x - (sprite.getX() - 8); // how many pixels to discard
                sprX += sprFifo.size(); // additionally, skip as many pixels as we already
                // have in the sprite se.liu.natho280.GbEmu.FIFOQueue
                int pixelRow = joinRowBytes(firstSpriteByte, secondSpriteByte);

                // add to fifo
                for (int i = sprX; i < 8; i++) {
                    int shift = sprite.getFlagXFlip() ? (i * 2) : (14 - (i * 2));
                    int pxColor = pixelRow & (0x3 << shift);
                    pxColor >>= shift;

                    sprFifo.add(new FIFOPixel(pxColor, sprite.getFlagPalette(), sprite.getFlagPriority()));
                }
            }
        }
    }

    private void buildSprFifo(int spriteHeight, int x, int ly) {
        // the sprite fifo should always be filled, but if there are no sprites to draw, it should be filled
        // with blank, low-prio pixels that will be replaced by the background
        if (sprFifo.size() < 8) {
            for (int i = (8 - sprFifo.size()); i > 0; i--) {
                sprFifo.add(new FIFOPixel(0, false, true));
            }
        }
        for (OAM sprite : lineOAMs) {
            // ensure that we are within the sprite; not before and not beyond
            if (x >= (sprite.getX() - 8) && x < sprite.getX()) {
                int spriteTileAddr = sprite.getTileAddress();

                int spriteRow = ly - (sprite.getY() - 16);
                if (sprite.getFlagYFlip()) spriteRow = (spriteHeight - 1) - spriteRow;
                spriteRow *= 2;

                int firstSpriteByte = memory.unconditionalRead(spriteTileAddr + spriteRow);
                int secondSpriteByte = memory.unconditionalRead(spriteTileAddr + spriteRow + 1);

                int sprX = x - (sprite.getX() - 8);

                int pixelRow = joinRowBytes(firstSpriteByte, secondSpriteByte);

                // go through each pixel, replace it if transparent or it is low-prio
                // i think this technically may not be accurate, but it's close enough
                for (int i = 0; i < 8; i++) {
                    if (sprFifo.get(i).colorValue == 0 || sprFifo.get(i).bgPrio) {
                        int shift = sprite.getFlagXFlip() ? ((i + sprX) * 2) : (14 - ((i + sprX) * 2));
                        int pxColor = pixelRow & (0x3 << shift);
                        pxColor >>= shift;

                        sprFifo.addIndex(new FIFOPixel(pxColor, sprite.getFlagPalette(), sprite.getFlagPriority()), i);
                    }
                }
            }
        }
    }

    /**
     * Helper function to grab bytes from tiles. Works for background tiles.
     * Documentation mentions tiles flipping, but that doesn't seem to be a thing for background tiles on DMG.
     * @param lcdc the LCD Control flags
     * @param y LY
     * @param fetcherX self-explanatory, will be 0-31
     * @return two bytes OR'd together, first one in top 8 bits, second one in bottom 8 bits
     */
    private int getBgOrWindowTileRow(int lcdc, int tilemapAdr, int y, int fetcherX) {
        // find out addressing mode!
        boolean unsignedAddressing = (lcdc & (1 << 4)) != 0;

        // divide y by 8 to find the row number.
        int tileAdrOffset = memory.unconditionalRead(tilemapAdr + (((y / 8) * 32 + fetcherX) & 0x3ff));
        // now to find the actual address to read the tile data from.
        int tileAddr = (unsignedAddressing ? (0x8000 + 16 * tileAdrOffset) : (0x9000 +  16 * (byte)tileAdrOffset));
        //if (tilemapAdr == 0x9C00) System.out.println("Tile address: 0x" + Integer.toHexString(tileAddr).toUpperCase());

        // this is the actual address of the tile we are looking up. THROUGH the tilemap!

        // now we find the y offset. this is how many bytes we skip ahead to get to our sought bytes.
        // putting it differently, this is the *row of the tile* that we read, or the tile-internal y-coordinate.
        int yOffset = (y % 8) * 2;
        return getTileRow(tileAddr, yOffset);
    }

    /**
     * Helper-helper function to grab 8 pixels from a tile with a given y coord. Should work for background tiles,
     * window tiles as well as sprite tiles -- but ensure that the input y coord actually corresponds to the row
     * OF THE TILE you want to fetch, NOT LY. y is INTERNAL here.
     * @param tileAddr address of the tile to fetch from
     * @param y row of the tile to fetch
     * @return two bytes OR'd together, first one in top 8 bits, second one in bottom 8 bits
     */
    private int getTileRow(int tileAddr, int y) {
        int firstByte = memory.unconditionalRead(tileAddr + y);
        int secondByte = memory.unconditionalRead(tileAddr + y + 1);
        return joinRowBytes(firstByte, secondByte);
    }

    /**
     * A row of pixels in a tile consists of two bytes, that are joined in an extraordinarily messed-up fashion.
     * For example, bytes 0000_0000 and 1111_1111 would join to make a row of 8 pixels like so: {0x10, 0x10, 0x10, 0x10
     * 0x10, 0x10, 0x10, 0x10}. To put it in words, we get the MSB of the first pixel from bit 7 of secondByte,
     * then the LSB of the first pixel from bit 7 of firstByte, then the same goes for bit 6 for the second pixel, etc.
     *
     * This is insane behaviour, so we use this method to grab the entire row cleanly as an int.
     * @param firstByte
     * @param secondByte
     * @return a row of 2-bit pixels, as an int
     */
    private int joinRowBytes(int firstByte, int secondByte) {
        int row = 0;
        for (int col = 0; col < 8; col++) {
            row |= (secondByte & (1 << col)) << (col + 1);
            row |= (firstByte & (1 << col)) << col;
        }
        return row;
    }

}
