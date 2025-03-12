package se.liu.natho280.gbemu;

/**
 * se.liu.natho280.GbEmu.Unsigned 8-bit number.
 * @see Unsigned
 */
public class UnsignedByte extends Unsigned {

    // unsigned byte, values range between 0 and 255

    public UnsignedByte(int value) {
        super(value);
    }

    protected int clamp(int value) {
        return (value & 0xFF);
    }


    public static void main(String[] args) {
        // testing
        UnsignedByte testUB = new UnsignedByte(255);
        System.out.println(testUB.get());

        testUB.set(testUB.get() + 1);
        System.out.println(testUB.get()); // value is now 0
        testUB.set(testUB.get() - 1);
        System.out.println(testUB.get()); // value is now 0

        testUB.set(0);
        testUB.set(testUB.get() - 100); // 156
        System.out.println(testUB.get());
    }
}
