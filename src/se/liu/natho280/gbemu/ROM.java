package se.liu.natho280.gbemu;

import java.io.BufferedInputStream;
import java.io.FileInputStream;

/**
 * Handles the game se.liu.natho280.GbEmu.ROM. Will set the se.liu.natho280.GbEmu.MBC (memory bank controller) appropriately, as long as it is a supported se.liu.natho280.GbEmu.MBC
 * (as of writing, only no-se.liu.natho280.GbEmu.MBC (se.liu.natho280.GbEmu.MBC0) and se.liu.natho280.GbEmu.MBC1 are supported).
 */
public class ROM {
    // this will be 0x4000 * N
    // the first
    private int chosenBank = 1; // should start at 1??
    private static final int ADDRESS_OFFSET = 0x4000;
    private static final int RAM_BANK_SIZE = 0x400;
    private MBC mbc = null; // se.liu.natho280.GbEmu.MBC is read from 0x147

    // se.liu.natho280.GbEmu.ROM size is read from 0x148, but we can just allocate the maximum possible (1.5 MiB)
    private final UnsignedByte[] rom = new UnsignedByte[0x180_000];
//    private final se.liu.natho280.GbEmu.UnsignedByte[] boot = new se.liu.natho280.GbEmu.UnsignedByte[256];
    private final UnsignedByte[] ram; // RAM size is read from cartridge! address 0x149

    public ROM(String romPath) {
        // load rom
        loadROM(romPath);

        // choose se.liu.natho280.GbEmu.MBC
        this.mbc = switch(rom[0x147].get()) {
            case 0 -> new MBC0();
            case 0x1 -> new MBC1(romBankNumber(rom[0x148].get()));
            default -> throw new IllegalArgumentException();
        };

        // pick RAM size
        this.ram = switch(rom[0x149].get()) {
            case 0 -> null;
            case 1 -> null;
            case 2 -> new UnsignedByte[RAM_BANK_SIZE];
            case 3 -> new UnsignedByte[RAM_BANK_SIZE * 4];
            case 4 -> new UnsignedByte[RAM_BANK_SIZE * 16];
            case 5 -> new UnsignedByte[RAM_BANK_SIZE * 8];
            default -> throw new IllegalArgumentException("Unknown RAM size: " + rom[0x149]);
        };
    }

    /**
     * Just for adding things as listeners to the se.liu.natho280.GbEmu.MBC.
     * @return
     */
    public MBC getMBC() {
        return this.mbc;
    }

    /**
     * Accesses the se.liu.natho280.GbEmu.ROM array with the input address redirected through the se.liu.natho280.GbEmu.MBC.
     * @param address
     * @return
     * @see MBC
     */
    public int get(int address) {
        if (address > 0x7FFF) throw new IllegalArgumentException("Should not try to access se.liu.natho280.GbEmu.ROM at address 0x" + Integer.toHexString(address).toUpperCase());
        try {
            return rom[mbc.redirectedAddress(address)].get();
        } catch (NullPointerException e) {
            e.printStackTrace();
            System.out.println("Cannot access se.liu.natho280.GbEmu.ROM at address 0x" + Integer.toHexString(mbc.redirectedAddress(address)).toUpperCase());
            System.exit(-1);
            return 0;
        }
    }

    /**
     * Writes to the se.liu.natho280.GbEmu.MBC registers.
     * @param address
     * @param value
     * @see MBC
     */
    public void write(int address, int value) {
        mbc.write(address, value);
    }

    /**
     * Loads the se.liu.natho280.GbEmu.ROM-file into the se.liu.natho280.GbEmu.ROM-array.
     * @param romPath path to se.liu.natho280.GbEmu.ROM-file as string, should be passed into program as the sole argument
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

        } catch (Exception e) {
            e.printStackTrace(); // can replace with "more robust logging"
        }
    }

    /**
     * Matches the number of banks in the se.liu.natho280.GbEmu.ROM to the value at 0x148,
     * part of the <a href=https://gbdev.io/pandocs/The_Cartridge_Header.html#0148--rom-size>cartridge header</a>.
     * @param memoryValue value at 0x148
     * @return the number of banks in the se.liu.natho280.GbEmu.ROM
     */
    private int romBankNumber(int memoryValue) {
        if (memoryValue < 0x9) {
            return (1 << (memoryValue + 1));
        }

        return switch (memoryValue) {
            case 0x52 -> 72;
            case 0x53 -> 80;
            case 0x54 -> 96;
            default -> throw new IllegalArgumentException("Unknown se.liu.natho280.GbEmu.ROM bank number: 0x" + Integer.toHexString(memoryValue).toUpperCase());
        };
    }
}
