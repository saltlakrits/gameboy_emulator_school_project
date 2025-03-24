package se.liu.natho280.gbemu.ppu;

/**
 * Small custom queues for FIFOPixels because the existing ones are a bit slow and clunky.
 * We have the benefit of not having to generalize anything in our implementation, and happily a 32-bit integer is
 * the perfect size for encoding a FIFOPixel as 4 bits. There should be two queues like this; one for sprite pixels,
 * and one for background pixels. They are filled a little differently from each other, but ultimately the PPU
 * pops one pixel off of each queue, makes a decision between them based on several factors, and pushes one to the
 * LCD.
 * @see <a href=https://gbdev.io/pandocs/pixel_fifo.html>Pan Docs - Pixel FIFO</a>
 * @see PPU#mode3
 */
public class FIFOQueue {
    private static final int CAPACITY = 8;
    private int bitfield = 0;
    private int length = 0;

    private static final int BITS_PER_NIBBLE = 4;
    private static final int NIBBLES_IN_QUEUE = 8;
    private static final int LEFTMOST_SHIFT = (NIBBLES_IN_QUEUE - 1) * BITS_PER_NIBBLE;
    private static final int LEFTMOST_MASK = (0xF << LEFTMOST_SHIFT);

    // Encoding: 2 bits for color, 1 bit for palette, 1 bit for prio
    // each element is 4 bits total, for a total of 32 bits which is fantastic

    /**
     * Encodes a FIFOPixel to 4 bits
     * @param fpx FIFOPixel
     * @return 4 bits signifying the FIFOPixel
     */
    private int encode(FIFOPixel fpx) {
        return ((fpx.colorValue << 2) |
                ((fpx.palette ? 1 : 0) << 1) |
                (fpx.bgPrio ? 1 : 0));
    }

    /**
     * Decodes 4 bits to a FIFOPixel
     * @param fpxBits 4 bits
     * @return a FIFOPixel
     */
    private FIFOPixel decode(int fpxBits) {
        return new FIFOPixel(fpxBits >>> 2,
                             (fpxBits & (1 << 1)) != 0,
                             (fpxBits & 1) != 0);
    }

    /**
     * Add a FIFOPixel to the end of the queue.
     * @param fpx a FIFOPixel
     */
    public void add(FIFOPixel fpx) {
        if (length == CAPACITY) {
            throw new IndexOutOfBoundsException("Queue is full");
        }

        length++;
        int shift = (((NIBBLES_IN_QUEUE - 1) - (length - 1)) * BITS_PER_NIBBLE);
        bitfield |= (encode(fpx) << shift);
    }

    /**
     * Add pixel at certain index, replacing what is already there
     * @param fpx a FIFOPixel
     */
    public void addIndex(FIFOPixel fpx, int index) {
        int shiftIndex = ((NIBBLES_IN_QUEUE - 1) - index) * BITS_PER_NIBBLE;
        bitfield &= (bitfield ^ (0xF << shiftIndex));
        bitfield |= (encode(fpx) << shiftIndex);
    }

    /**
     * Get a FIFOPixel at the given index (this is like an array index, not a bit-index).
     * @param index index, 0 == first pixel in the queue
     * @return a FIFOPixel at the given index
     */
    public FIFOPixel get(int index) {
        int shiftIndex = ((NIBBLES_IN_QUEUE - 1) - index) * BITS_PER_NIBBLE;
        return decode((bitfield >> shiftIndex) & 0xF);
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

        // Shift a nibble out, dec length
        bitfield <<= BITS_PER_NIBBLE;
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
     * @return the oldest FIFOPixel in the queue.
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
