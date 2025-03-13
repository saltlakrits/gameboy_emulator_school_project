package se.liu.natho280.gbemu.rom;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.debugger.MBCListener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * se.liu.natho280.GbEmu.MBC1, the simplest se.liu.natho280.GbEmu.MBC. See the link for information.
 * NOTE: This class is halfway implemented, as such there are warnings!
 * @see <a href=https://gbdev.io/pandocs/MBC1.html>Pan Docs - se.liu.natho280.GbEmu.MBC1</a>
 */
public class MBC1 implements MBC {
    private static final int ADDRESS_OFFSET = 0x4000;
    private int numberOfBanks;
    private int romBank = 0;
    private int highBank = 0;
    private boolean ramEnabled = false;
    private int ramBank = 0;
    private boolean advancedBankingMode = false;

    private List<MBCListener> mbcListeners = new ArrayList<>();

    public MBC1(int numberOfBanks) {
        this.numberOfBanks = numberOfBanks;
        CuteLogger.log(Level.INFO, "We have " + numberOfBanks + " banks in this rom");
    }

    @Override
    public int redirectedAddress(int address) {
        // example: rom.get(address) -> rom[mbc.redirectedAddress(address)]
        if (address <= 0x3FFF) {
            return address;
        }
        if (address <= 0x7FFF) {
            // if we are looking at 0x4000, and bank is 2, we should get back 0x8000
            // Rombank size : 0x4000
            return address + (((romBank == 0 ? 1 : romBank) - 1) * ADDRESS_OFFSET);
//            return ((address & 0x3FFF) | ((romBank == 0 ? 1 : romBank) << 14));
        }
        // else something went wrong
        throw new IllegalArgumentException("Invalid address: " + address);
    }

    @Override
    public void write(int address, int value) {
        if (address <= 0x1FFF) {
            // RAM enable register
            System.out.println("Writing 0x" + Integer.toHexString(address).toUpperCase() + " to RAM enable");
            if (value == 0xA) {
                ramEnabled = true;
            } else {
                ramEnabled = false;
            }
        } else if (address <= 0x3FFF) {
            // se.liu.natho280.GbEmu.ROM bank number
            //System.out.println("switching to bank " + Integer.toHexString(value).toUpperCase());
            this.romBank = (value & 0x1F) % numberOfBanks;
        } else if (address <= 0x5FFF) {
            // RAM bank number --OR-- upper bits of se.liu.natho280.GbEmu.ROM bank number
            this.highBank = value & 0x3;
            //System.out.println("Wrote " + Integer.toHexString(value).toUpperCase() + " to upper bank thing");
        } else {
            // Banking mode select
            //System.out.println("Setting banking mode to " + (value == 0 ? "simple" : "advanced"));
            this.advancedBankingMode = value != 0;
        }

        for (MBCListener listener : mbcListeners) {
            listener.bankSwitched();
        }
    }

    public void addMBCListener(MBCListener l) {
        this.mbcListeners.add(l);
    }
}
