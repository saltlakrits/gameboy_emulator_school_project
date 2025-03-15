package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.debugger.MBCListener;
import se.liu.natho280.gbemu.serialization.SerializableMBC;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
}
