package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.serialization.SerializableMBC;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;
import se.liu.natho280.gbemu.cpu.UnsignedByte;
import se.liu.natho280.gbemu.debugger.MBCListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles the game ROM. Will set the MBC (memory bank controller) appropriately, as long as it is a supported MBC (as of writing, only
 * no-MBC (MBC0) and MBC1 are supported).
 */
public class ROM implements Serializable {
    // this will be 0x4000 * N

    private static final int RAM_BANK_SIZE = 0x2000; // 8 KiB
    private transient AbstractMBC mbc = null; // MBC is read from 0x147
    private String romPath;
    private boolean battery = false;

    // ROM size is read from 0x148, but we can just allocate the maximum possible (1.5 MiB)
    private UnsignedByte[] rom = new UnsignedByte[0x180_000];
    //    private final UnsignedByte[] boot = new UnsignedByte[256];
    private UnsignedByte[] ram; // RAM size is read from cartridge! address 0x149

    public ROM(String romPath) throws IllegalStateException {

        // set ROM name
        this.romPath = extractROMpath(romPath);

        // load rom
        loadROM(romPath);

        // choose MBC
        selectMBC();

	// pick RAM size

        CuteLogger.log(Level.INFO, "Ram size in header: " + rom[0x149].get());
        switch (rom[0x149].get()) {
            case 0, 1:
                this.ram = null;
                break;
            case 2:
                this.ram = new UnsignedByte[RAM_BANK_SIZE];
                break;
            case 3:
                this.ram = new UnsignedByte[RAM_BANK_SIZE * 4];
                break;
            case 4:
                this.ram = new UnsignedByte[RAM_BANK_SIZE * 16];
                break;
            case 5:
                this.ram = new UnsignedByte[RAM_BANK_SIZE * 8];
                break;
            default:
                this.ram = null; // doesn't matter, exiting
                CuteLogger.log(Level.SEVERE, "Unrecognized RAM size: " + rom[0x149].get());
                throw new IllegalStateException("Failed to load ROM!\nError: " + "Unrecognized RAM size: " + rom[0x149].get());
        }

        // If no save file found, but there is ram:
        if (ram != null) {
            // check if there is a save file
            if (battery && new File(this.romPath + ".sav").exists()) {
                loadRAM();
            } else {
                // if there is no save file, init RAM
                for (int i = 0; i < ram.length; i++) {
                    // initialize RAM
                    ram[i] = new UnsignedByte(0);
                }
            }
        }
    }

    public ROM() {}

    /**
     * Extracts the rom path without the file extension
     * @param romPath path to ROM file, including file extension, if there is one
     * @return romPath without the file extension, if there was one
     */
    private String extractROMpath(String romPath) {
        String[] splitPath = romPath.split("\\.");
        if (splitPath.length > 2) {
            romPath = "";
            for (int i = 0; i < splitPath.length - 1; i++) {
                romPath += splitPath[i];
            }
            return romPath;
        } else {
            return splitPath[0];
        }
    }

    /**
     * Selects an MBC depending on info in <a href=https://gbdev.io/pandocs/The_Cartridge_Header.html>Cartridge Header - Pan Docs</a>
     * @throws IllegalStateException
     */
    private void selectMBC() throws IllegalStateException {
        switch (rom[0x147].get()) {
            case 0:
                CuteLogger.log(Level.INFO, "Picked MBC0.");
                this.mbc = new MBC0();
                break;
            case 1:

                CuteLogger.log(Level.INFO, "Picked MBC1.");
                this.mbc = new MBC1(romBankNumber(rom[0x148].get()));
                break;
            case 2:
                CuteLogger.log(Level.INFO, "Picked MBC1 + RAM.");
                this.mbc = new MBC1(romBankNumber(rom[0x148].get()));
                break;
            case 3:
                CuteLogger.log(Level.INFO, "Picked MBC1 + RAM + Battery.");
                this.mbc = new MBC1(romBankNumber(rom[0x148].get()));
                this.battery = true;
                break;
            default:
                CuteLogger.log(Level.SEVERE, "Unknown MBC Type: " + rom[0x148].get());
                throw new IllegalStateException("Failed to load ROM!\nError: Unknown MBC Type: " + rom[0x148].get());
        }
    }

    /**
     * Restore game state from file.
     * @param serializationWrapper
     * @param mbcListeners
     */
    public void restoreState(SerializationWrapper serializationWrapper, List<MBCListener> mbcListeners) {
        ROM serializedROM = serializationWrapper.getMemory().getROM();
        this.rom = serializedROM.rom;
        this.ram = serializedROM.ram;
        this.romPath = serializedROM.romPath;
        this.battery = serializedROM.battery;

        this.mbc = serializationWrapper.getMBC();
        for (MBCListener listener : mbcListeners) {
            this.mbc.addListener(listener);
        }
    }

    /**
     * Just for adding things as listeners to the MBC.
     *
     * @return
     */
    public SerializableMBC getSerializableMBC() {
        return this.mbc.makeSerializable();
    }

    public void addMBCListener(MBCListener l) {
        this.mbc.addListener(l);
    }

    public List<MBCListener> getMBCListeners() {
        return this.mbc.getListeners();
    }

    /**
     * Accesses the ROM array with the input address redirected through the MBC.
     *
     * @param address
     *
     * @return
     * @see AbstractMBC
     */
    public int get(int address) {
        if (address >= 0xA000 && address <= 0xBFFF) {
            if (!mbc.getRamEnabled()) return 0xFF;
            return ram[mbc.redirectedAddress(address)].get();
        }
        return rom[mbc.redirectedAddress(address)].get();
    }

    /**
     * Writes to the MBC registers.
     *
     * @param address
     * @param value
     *
     * @see MBC
     */
    public void write(int address, int value) {
        if (address >= 0xA000 && address <= 0xBFFF && ram != null) {
            ram[mbc.redirectedAddress(address)].set(value);
        }
        mbc.write(address, value);
    }

    /**
     * Loads the ROM-file into the ROM-array.
     *
     * @param romPath path to ROM-file as string, should be passed into program as the sole argument
     */
    private void loadROM(String romPath) throws IllegalStateException {

        try (FileInputStream fis = new FileInputStream(romPath)) {
            CuteLogger.log(Level.INFO, "ROM: " + romPath);
            BufferedInputStream bis = new BufferedInputStream(fis);

            int readByte;

            int index = 0;
            while ((readByte = bis.read()) != -1) {
                if (index >= 0x180_000) {
                    CuteLogger.log(Level.SEVERE, "ROM file is too big for the ROM array, i.e. malformed ROM binary");
                    throw new IndexOutOfBoundsException("ROM file is too big for the ROM array, i.e. malformed ROM binary");
                }
                rom[index] = new UnsignedByte(readByte);
                index++;
            }

        } catch (IOException | IndexOutOfBoundsException e) {
            CuteLogger.log(Level.SEVERE, "ROM load error: " + e.getMessage());
            // ignores the thrown exception in favor of throwing a more generic one to signify a failed ROM load
            throw new IllegalStateException("Failed to load ROM!\nError: " + e.getMessage());
        }
    }

    /**
     * Matches the number of banks in the ROM to the value at 0x148, part of the <a
     * href=https://gbdev.io/pandocs/The_Cartridge_Header.html#0148--rom-size>cartridge header</a>.
     *
     * @param memoryValue value at 0x148
     *
     * @return the number of banks in the ROM
     */
    private int romBankNumber(int memoryValue) throws IllegalStateException {
        if (memoryValue < 0x9) {
            return (1 << (memoryValue + 1));
        }

        switch (memoryValue) {
            case 0x52:
                return 72;
            case 0x53:
                return 80;
            case 0x54:
                return 96;
            default:
                CuteLogger.log(Level.SEVERE, "Unknown ROM bank number: " + Integer.toHexString(romBankNumber(memoryValue)).toUpperCase());
//                System.exit(-1);
                throw new IllegalStateException("Failed to load ROM!\nError: " + "Unknown ROM bank number: " + Integer.toHexString(romBankNumber(memoryValue)).toUpperCase());
        }
    }

    /**
     * @return a copy of the ROM object
     */
    public ROM copy() {
        ROM copyROM = new ROM();
        copyROM.rom = this.rom.clone();
        copyROM.ram = this.ram == null ? null : this.ram.clone();
        copyROM.mbc = this.mbc == null ? null : this.mbc.copy();

        return copyROM;
    }

    public void reset() {
        selectMBC();
    }

    /**
     * Save RAM to disk, for games that implement saving
     */
    public void saveRAM() {
        if (this.ram != null && battery) this.mbc.saveRAM(romPath, this.ram);
    }

    /**
     * Load RAM from disk
     */
    public void loadRAM() {
        UnsignedByte[] ram = null;
        if (this.ram != null) {
            ram = this.mbc.loadRAM(romPath);
        }

        if (ram != null) {
            this.ram = ram;
        }
    }
}
