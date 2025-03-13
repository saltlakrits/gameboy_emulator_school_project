package se.liu.natho280.gbemu.cpu;

/**
 * se.liu.natho280.GbEmu.Unsigned 8-bit number.
 * @see Unsigned
 */
public class UnsignedByte extends Unsigned {

    // unsigned byte, values range between 0 and 255

    public UnsignedByte(int value) {
        super(value);
    }

    @Override
    protected int clamp(int value) {
        return (value & 0xFF);
    }
}
