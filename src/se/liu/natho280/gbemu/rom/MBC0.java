package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.serialization.MBCType;
import se.liu.natho280.gbemu.serialization.SerializableMBC;

/**
 * No MBC. Simple games like Tetris and Dr. Mario fit into 32 KiB, and don't need bank switching.
 * @see MBC
 */
public class MBC0 extends AbstractMBC {

    @Override
    public void write(int address, int value) {}

    @Override
    public int redirectedAddress(int address) {
        return address;
    }

    @Override public SerializableMBC makeSerializable() {
        SerializableMBC smbc = new SerializableMBC(MBCType.MBC0, new int[0]);

        return smbc;
    }

}
