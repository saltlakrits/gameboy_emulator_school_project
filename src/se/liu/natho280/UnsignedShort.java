/**
 * Unsigned 16-bit numbers.
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
