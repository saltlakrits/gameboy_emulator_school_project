package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.cpu.UnsignedByte;
import se.liu.natho280.gbemu.serialization.MBCType;
import se.liu.natho280.gbemu.serialization.SerializableMBC;

/**
 * No MBC. Simple games like Tetris and Dr. Mario fit into 32 KiB, and don't need bank switching.
 * @see MBC
 */
public class MBC0 extends AbstractMBC {

    /**
     * There are no registers to set on MBC0, so writes are ignored (games for MBC0 shouldn't write to it anyway,
     * so this might hide bugs, theoretically).
     * @param address
     * @param value
     */
    @Override
    public void write(int address, int value) {}

    /**
     * No redirecting for MBC0.
     * @param address
     * @return
     */
    @Override
    public int redirectedAddress(int address) {
        return address;
    }

    /**
     * @see SerializableMBC
     */
    @Override public SerializableMBC makeSerializable() {
        SerializableMBC smbc = new SerializableMBC(MBCType.MBC0, new int[0]);

        return smbc;
    }

    /**
     * As MBC0 has no real state, this just returns a fresh instance of the class.
     */
    @Override public AbstractMBC copy() {
        return new MBC0();
    }

    /**
     * MBC0 never has any RAM, as such return false unconditionally.
     */
    @Override public boolean getRamEnabled() {
        return false;
    }

    /**
     * MBC0 never has any RAM, as such ignore the call.
     */
    @Override public void saveRAM(final String fileName, final UnsignedByte[] ram) {}

    /**
     * MBC0 never has any RAM, this is only here to complete implementation.
     */
    @Override public UnsignedByte[] loadRAM(final String fileName) {
        return null;
    }

}
