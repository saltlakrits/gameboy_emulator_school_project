package se.liu.natho280.gbemu.cpu;

/**
 * Unsigned 8-bit number, concrete implementation of Unsigned abstract class. In practice, the only difference between our different
 * Unsigned classes are the clamp() methods.
 * @see Unsigned
 */
public class UnsignedByte extends Unsigned {
    private static final int BYTE_MAX = 0xFF; // 8 bits, 255 is the highest unsigned value

    public UnsignedByte(int value) {
        super(value);
    }

    @Override
    protected int clamp(int value) {
        return (value & BYTE_MAX);
    }
}
