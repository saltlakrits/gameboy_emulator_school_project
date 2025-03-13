package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.debugger.MBCListener;

import java.util.ArrayList;
import java.util.List;

/**
 * No MBC. Simple games like Tetris and Dr. Mario fit into 32 KiB, and don't need bank switching.
 * @see MBC
 */
public class MBC0 implements MBC {
    List<MBCListener> mbcListeners = new ArrayList<>();

    @Override
    public void write(int address, int value) {}

    @Override
    public int redirectedAddress(int address) {
        return address;
    }

    @Override
    public void addMBCListener(MBCListener l) {
        mbcListeners.add(l);
    }

    @Override
    public List<MBCListener> getListeners() {
        return new ArrayList<>(mbcListeners);
    }
}
