package se.liu.natho280.gbemu.cpu;

/**
 * Unsigned 8-bit number.
 * @see Unsigned
 */
public class UnsignedByte extends Unsigned {
    public UnsignedByte(int value) {
        super(value);
    }

    @Override
    protected int clamp(int value) {
        return (value & 0xFF);
    }
}
