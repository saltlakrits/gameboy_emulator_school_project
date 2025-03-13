package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.UnsignedByte;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Handles the game ROM. Will set the MBC (memory bank controller) appropriately, as long as it is a supported MBC
 * (as of writing, only no-MBC, MBC0) and MBC1 are supported).
 */
public class ROM {
    // this will be 0x4000 * N
    // the first
    private static final int RAM_BANK_SIZE = 0x400;
    private MBC mbc = null; // MBC is read from 0x147

    // ROM size is read from 0x148, but we can just allocate the maximum possible (1.5 MiB)
    private final UnsignedByte[] rom = new UnsignedByte[0x180_000];
//    private final UnsignedByte[] boot = new UnsignedByte[256];
    private final UnsignedByte[] ram; // RAM size is read from cartridge! address 0x149

    public ROM(String romPath) {
        // load rom
        loadROM(romPath);

        // choose MBC

        switch (rom[0x147].get()) {
            case 0:
                this.mbc = new MBC0();
                break;
            case 0x1:
                this.mbc = new MBC1(romBankNumber(rom[0x148].get()));
                break;
            default:
                CuteLogger.log(Level.SEVERE, "Unknown MBC Type: " + rom[0x148].get());
                System.exit(-1);
        }

        // pick RAM size
        switch(rom[0x149].get()) {
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
                System.exit(-1);
        }
    }

    /**
     * Just for adding things as listeners to the MBC.
     * @return
     */
    public MBC getMBC() {
        return this.mbc;
    }

    /**
     * Accesses the ROM array with the input address redirected through the MBC.
     * @param address
     * @return
     * @see MBC
     */
    public int get(int address) {
        return rom[mbc.redirectedAddress(address)].get();
    }

    /**
     * Writes to the MBC registers.
     * @param address
     * @param value
     * @see MBC
     */
    public void write(int address, int value) {
        mbc.write(address, value);
    }

    /**
     * Loads the ROM-file into the ROM-array.
     * @param romPath path to ROM-file as string, should be passed into program as the sole argument
     */
    private void loadROM(String romPath) {

        try (FileInputStream fis = new FileInputStream(romPath)) {
            System.out.println(romPath);
            BufferedInputStream bis = new BufferedInputStream(fis);

            int readByte;

            int index = 0;
            while ((readByte = bis.read()) != -1) {
                rom[index] = new UnsignedByte(readByte);
                index++;
            }

        } catch (IOException e) {
	    CuteLogger.log(Level.SEVERE, e.getMessage());
            System.err.println("Failed to load ROM: " + romPath);
            System.exit(-1);
	}
    }

    /**
     * Matches the number of banks in the ROM to the value at 0x148,
     * part of the <a href=https://gbdev.io/pandocs/The_Cartridge_Header.html#0148--rom-size>cartridge header</a>.
     * @param memoryValue value at 0x148
     * @return the number of banks in the ROM
     */
    private int romBankNumber(int memoryValue) {
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
                CuteLogger.log(Level.SEVERE,
                               "Unknown ROM bank number: " +
                               Integer.toHexString(romBankNumber(memoryValue)).toUpperCase());
                System.exit(-1);
        }

        return 0; // unreachable
    }
}
