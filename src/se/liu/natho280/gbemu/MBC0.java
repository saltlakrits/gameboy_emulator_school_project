package se.liu.natho280.gbemu;

/**
 * No se.liu.natho280.GbEmu.MBC. Simple games like Tetris and Dr. Mario fit into 32 KiB, and don't need bank switching.
 * @see MBC
 */
public class MBC0 implements MBC {
    @Override
    public void write(int address, int value) {}

    @Override
    public int redirectedAddress(int address) {
        return address;
    }

    @Override
    public void addMBCListener(MBCListener l) {}
}
