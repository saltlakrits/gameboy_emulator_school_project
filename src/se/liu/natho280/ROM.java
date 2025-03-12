import java.io.BufferedInputStream;
import java.io.FileInputStream;

/**
 * Handles the game ROM. Will set the MBC (memory bank controller) appropriately, as long as it is a supported MBC
 * (as of writing, only no-MBC (MBC0) and MBC1 are supported).
 */
public class ROM {
    // this will be 0x4000 * N
    // the first
    private int chosenBank = 1; // should start at 1??
    private static final int ADDRESS_OFFSET = 0x4000;
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
        if (address > 0x7FFF) throw new IllegalArgumentException("Should not try to access ROM at address 0x" + Integer.toHexString(address).toUpperCase());
        try {
            return rom[mbc.redirectedAddress(address)].get();
        } catch (NullPointerException e) {
            e.printStackTrace();
            System.out.println("Cannot access ROM at address 0x" + Integer.toHexString(mbc.redirectedAddress(address)).toUpperCase());
            System.exit(-1);
            return 0;
        }
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

        } catch (Exception e) {
            e.printStackTrace(); // can replace with "more robust logging"
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

        return switch (memoryValue) {
            case 0x52 -> 72;
            case 0x53 -> 80;
            case 0x54 -> 96;
            default -> throw new IllegalArgumentException("Unknown ROM bank number: 0x" + Integer.toHexString(memoryValue).toUpperCase());
        };
    }
}
