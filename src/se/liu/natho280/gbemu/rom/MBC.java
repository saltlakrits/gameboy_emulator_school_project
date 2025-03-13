package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.debugger.MBCListener;

import java.util.List;

/**
 * Memory Bank Controllers, MBCs, are what allows Game Boy cartridges to contain more than 32 KiB, and/or have RAM in
 * the cart. It achieves this by letting the program choose a memory bank of the cartridge by writing to registers
 * in the MBC, and redirecting the address. Addresses will always be between 0x0000 and 0x7FFF when accessing the ROM,
 * but will point to different sections, banks, of the ROM.
 * @see <a href=https://gbdev.io/pandocs/MBCs.html>Pan Docs - MBCs</a>
 */
public interface MBC {
    public void write(int address, int value);
    public int redirectedAddress(int address);
    public void addMBCListener(MBCListener l);
    public List<MBCListener> getListeners();
}
