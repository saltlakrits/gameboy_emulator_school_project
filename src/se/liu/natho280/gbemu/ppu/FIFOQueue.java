package se.liu.natho280.gbemu.ppu;

/**
 * Small custom queues for FIFOPixels because the existing ones are a bit slow and clunky.
 * We have the benefit of not having to generalize anything in our implementation, and happily a 32-bit integer is
 * the perfect size for encoding a se.liu.natho280.GbEmu.FIFOPixel as 4 bits. There should be two queues like this; one for sprite pixels,
 * and one for background pixels. They are filled a little differently from each other, but ultimately the se.liu.natho280.GbEmu.PPU
 * pops one pixel off of each queue, makes a decision between them based on several factors, and pushes one to the
 * LCD.
 * @see <a href=https://gbdev.io/pandocs/pixel_fifo.html>Pan Docs - se.liu.natho280.GbEmu.Pixel FIFO</a>
 * @see PPU#mode3
 */
public class FIFOQueue {
    private static final int CAPACITY = 8;
    private int bitfield = 0;
    private int length = 0;

    private static final int LEFTMOST_SHIFT = 7 * 4;
    private static final int LEFTMOST_MASK = (0xF << LEFTMOST_SHIFT);

    // Encoding: 2 bits for color, 1 bit for palette, 1 bit for prio
    // each element is 4 bits total, for a total of 32 bits which is fantastic

    /**
     * Encodes a se.liu.natho280.GbEmu.FIFOPixel to 4 bits
     * @param fpx se.liu.natho280.GbEmu.FIFOPixel
     * @return 4 bits signifying the se.liu.natho280.GbEmu.FIFOPixel
     */
    private int encode(FIFOPixel fpx) {
        return ((fpx.colorValue << 2) | ((fpx.palette ? 1 : 0) << 1) | (fpx.bgPrio ? 1 : 0));
    }

    /**
     * Decodes 4 bits to a se.liu.natho280.GbEmu.FIFOPixel
     * @param fpxBits 4 bits
     * @return a se.liu.natho280.GbEmu.FIFOPixel
     */
    private FIFOPixel decode(int fpxBits) {
        return new FIFOPixel(fpxBits >>> 2, (fpxBits & 0x2) != 0, (fpxBits & 0x1) != 0);
    }

    /**
     * Add a se.liu.natho280.GbEmu.FIFOPixel to the end of the queue.
     * @param fpx a se.liu.natho280.GbEmu.FIFOPixel
     */
    public void add(FIFOPixel fpx) {
        if (length == CAPACITY) {
            throw new IndexOutOfBoundsException("Queue is full");
        }

        length++;
        bitfield |= (encode(fpx) << ((7 - (length - 1)) * 4));
    }

    /**
     * Add pixel at certain index, replacing what is already there
     * @param fpx a se.liu.natho280.GbEmu.FIFOPixel
     */
    public void addIndex(FIFOPixel fpx, int index) {
//        bitfield &= (0xF << (index * 4));
        bitfield &= (bitfield ^ (0xF << ((7 - index) * 4)));
        bitfield |= (encode(fpx) << ((7 - index) * 4));
    }

    /**
     * Get a se.liu.natho280.GbEmu.FIFOPixel at the given index (this is like an array index, not a bit-index).
     * @param index index, 0 == first pixel in the queue
     * @return a se.liu.natho280.GbEmu.FIFOPixel at the given index
     */
    public FIFOPixel get(int index) {
        return decode((bitfield >> ((7 - index) * 4)) & 0xF);
    }

    /**
     * Pops the first item off the queue, and shifts the bitfield. The oldest item inserted is the first to be popped,
     * unless you went out of your way to set items by index instead of pushing them in the back of the queue.
     * @return
     */
    public FIFOPixel pop() {
        if (length == 0) {
            throw new IndexOutOfBoundsException("The queue is empty");
        }

        FIFOPixel fpx = getFirst();
        bitfield <<= 4;
        length--;

        return fpx;
    }

    /**
     * Zeroes out the bitfield, clearing the queue and resetting the length.
     */
    public void clear() {
        bitfield = 0;
        length = 0;
    }

    /**
     * @return true if queue is empty, else 0
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Peek at the oldest item in the queue without popping it.
     * @return the oldest se.liu.natho280.GbEmu.FIFOPixel in the queue.
     */
    public FIFOPixel getFirst() {
        if (isEmpty()) {
            throw new IndexOutOfBoundsException("The queue is empty");
        }
        int fpxBits = ((bitfield & LEFTMOST_MASK) >>> LEFTMOST_SHIFT);
        return decode(fpxBits);
    }

    /**
     * Returns the size (or length) of the queue.
     * @return the size of the queue
     */
    public int size() {
        return length;
    }
}
