package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.serialization.SerializableMBC;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;
import se.liu.natho280.gbemu.cpu.UnsignedByte;
import se.liu.natho280.gbemu.debugger.MBCListener;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles the game ROM. Will set the MBC (memory bank controller) appropriately, as long as it is a supported MBC (as of writing, only
 * no-MBC, MBC0) and MBC1 are supported).
 */
public class ROM implements Serializable {
    // this will be 0x4000 * N
    // the first

    private static final int RAM_BANK_SIZE = 0x2000; // 8 KiB
    private transient AbstractMBC mbc = null; // MBC is read from 0x147

    // ROM size is read from 0x148, but we can just allocate the maximum possible (1.5 MiB)
    private UnsignedByte[] rom = new UnsignedByte[0x180_000];
    //    private final UnsignedByte[] boot = new UnsignedByte[256];
    private UnsignedByte[] ram; // RAM size is read from cartridge! address 0x149

    public ROM(String romPath) throws IllegalArgumentException {

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
                CuteLogger.log(Level.SEVERE, "Unknown MBC Type: " + rom[0x149].get());
                throw new IllegalArgumentException("Failed to load ROM!");
//                System.exit(-1);
        }

        // If no save file found, but there is ram:
        if (ram != null) {
            for (int i = 0; i < ram.length; i++) {
                // initialize RAM
                ram[i] = new UnsignedByte(0);
            }
            // else load save file into ram:
        }
    }

    private void selectMBC() throws IllegalArgumentException {
        switch (rom[0x147].get()) {
            case 0:
                CuteLogger.log(Level.INFO, "Picked MBC0.");
                this.mbc = new MBC0();
                break;
            case 1, 2, 3:
                CuteLogger.log(Level.INFO, "Picked MBC1.");
                this.mbc = new MBC1(romBankNumber(rom[0x148].get()));
                break;
            default:
                CuteLogger.log(Level.SEVERE, "Unknown MBC Type: " + rom[0x148].get());
                throw new IllegalArgumentException("Failed to load ROM!");
//                System.exit(-1);
        }
    }

    public ROM() {}

    public void restoreState(SerializationWrapper serializationWrapper, List<MBCListener> mbcListeners) {
        ROM serializedROM = serializationWrapper.getMemory().getROM();
        this.rom = serializedROM.rom;
        this.ram = serializedROM.ram;

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
    private void loadROM(String romPath) throws IllegalArgumentException {

        try (FileInputStream fis = new FileInputStream(romPath)) {
            CuteLogger.log(Level.INFO, "ROM: " + romPath);
            BufferedInputStream bis = new BufferedInputStream(fis);

            int readByte;

            int index = 0;
            while ((readByte = bis.read()) != -1) {
                rom[index] = new UnsignedByte(readByte);
                index++;
            }

        } catch (IOException e) {
            CuteLogger.log(Level.SEVERE, e.getMessage());
            // ignores the thrown exception in favor of throwing a more generic one to signify a failed ROM load
            throw new IllegalArgumentException("Failed to load ROM!");
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
    private int romBankNumber(int memoryValue) throws IllegalArgumentException {
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
                throw new IllegalArgumentException("Failed to load ROM!");
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
}
