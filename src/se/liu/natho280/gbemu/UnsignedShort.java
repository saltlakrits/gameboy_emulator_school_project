package se.liu.natho280.gbemu;

/**
 * se.liu.natho280.GbEmu.Unsigned 16-bit numbers.
 * @see Unsigned
 */
public class UnsignedShort extends Unsigned {
    public UnsignedShort(int value) {
        super(value);
    }

    protected int clamp(int value) {
        return (value & 0xFFFF);
    }
}
