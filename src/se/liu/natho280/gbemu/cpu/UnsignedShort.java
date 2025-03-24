package se.liu.natho280.gbemu.cpu;

/**
 * Unsigned 16-bit number, concrete implementation of Unsigned abstract class. In practice, the only difference between our different
 * Unsigned classes are the clamp() methods.
 * @see Unsigned
 */
public class UnsignedShort extends Unsigned {
    public UnsignedShort(int value) {
        super(value);
    }

    @Override
    protected int clamp(int value) {
        return (value & 0xFFFF);
    }
}
