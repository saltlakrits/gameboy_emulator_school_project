package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.cpu.UnsignedByte;
import se.liu.natho280.gbemu.debugger.MBCListener;
import se.liu.natho280.gbemu.serialization.SerializableMBC;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>The MBCs (Memory Bank Controllers) were chips in the cartridges that could switch between memory banks. The Game Boy cartridges contained
 * memory that could exceed the number of available addresses, and as such MBCs would let you determine which part of the cartridge ROM
 * was available at a given time. Since they can differ internally, but will always offer the same functionality on the outside,
 * an abstract class is a perfect choice for the project. A ROM will always have an MBC, but the MBC can do anything between nothing and
 * re-routing the addresses to different banks, have internal clocks, et cetera.</p>
 *
 * <p>More can be read at <a href=https://gbdev.io/pandocs/MBCs.html>MBCs - Pan Docs</a>.</p>
 */
public abstract class AbstractMBC implements Serializable
{
    protected transient List<MBCListener> mbcListeners = new ArrayList<>();

    public List<MBCListener> getListeners() {
	return new ArrayList<>(mbcListeners);
    }

    public void addListener(MBCListener listener) {
	this.mbcListeners.add(listener);
    }

    public void clearListeners() {
	this.mbcListeners.clear();
    }

    abstract public void write(int address, int value);

    abstract public int redirectedAddress(int address);

    abstract public SerializableMBC makeSerializable();

    abstract public AbstractMBC copy();

    abstract public boolean getRamEnabled();

    abstract public void saveRAM(final String fileName, final UnsignedByte[] ram);

    abstract public UnsignedByte[] loadRAM(final String fileName);
}
