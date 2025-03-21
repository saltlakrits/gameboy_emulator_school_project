package se.liu.natho280.gbemu.rom;

import com.google.gson.Gson;
import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.UnsignedByte;
import se.liu.natho280.gbemu.debugger.MBCListener;
import se.liu.natho280.gbemu.serialization.MBCType;
import se.liu.natho280.gbemu.serialization.SerializableMBC;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

/**
 * MBC1, the simplest MBC. See the link for information.
 * NOTE: This class is halfway implemented, as such there are warnings!
 *
 * Battery (saving) and RAM functionality is baked into this class!
 *
 * @see <a href=https://gbdev.io/pandocs/MBC1.html>Pan Docs - MBC1</a>
 */
public class MBC1 extends AbstractMBC {
    private static final int ADDRESS_OFFSET = 0x4000;
    private int numberOfBanks;
    private int romBank = 0;
    private int highBank = 0;
    private boolean ramEnabled = false;
    private int ramBank = 0;
    private boolean advancedBankingMode = false;

    public MBC1(int numberOfBanks) {
        this.numberOfBanks = numberOfBanks;
        CuteLogger.log(Level.INFO, "We have " + numberOfBanks + " banks in this rom");
    }

    public MBC1(SerializableMBC mbc) {
        this.numberOfBanks = mbc.mbcThings[0];
        this.romBank = mbc.mbcThings[1];
        this.highBank = mbc.mbcThings[2];
        this.ramEnabled = mbc.mbcThings[3] == 1;
        this.ramBank = mbc.mbcThings[4];
        this.advancedBankingMode = mbc.mbcThings[5] == 1;
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
        if (address >= 0xA000 && address <= 0xBFFF) {
            // do stuff to the ram address!
            // FIXME principally correct, may be off by one..?
            if ((address - 0xA000 + (ramBank * 0x2000)) >= 0x2000) {
                CuteLogger.log(Level.SEVERE, Integer.toHexString((address - 0xA000 + (ramBank * 0x2000))).toUpperCase() + " is too big");
            }
            return address - 0xA000 + (ramBank * 0x2000);
        }
        // else something went wrong
        throw new IllegalArgumentException("Invalid address: " + address);
    }

    public boolean getRamEnabled() {
        return ramEnabled;
    }

    @Override public void saveRAM(final String fileName, final UnsignedByte[] ram) {
        Gson gson = new Gson();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName + ".sav"))) {
            gson.toJson(ram, writer);
        } catch (IOException e) {
	    CuteLogger.log(Level.SEVERE, "Failed to save RAM! Error: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Failed to save RAM! Error: " + e.getMessage(), "Error!", JOptionPane.ERROR_MESSAGE);
	}
    }

    @Override public UnsignedByte[] loadRAM(final String fileName) {
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName + ".sav"))) {
            CuteLogger.log(Level.INFO, "Loading RAM from file: " + fileName + ".sav");
            return gson.fromJson(reader, UnsignedByte[].class);
        } catch (IOException e) {
            CuteLogger.log(Level.SEVERE, "Failed to load RAM! Error: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Failed to load RAM! Error: " + e.getMessage(), "Error!", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    @Override
    public SerializableMBC makeSerializable() {
        // prepare int array to save information
        int[] mbcThings = new int[6];
        mbcThings[0] = numberOfBanks;
        mbcThings[1] = romBank;
        mbcThings[2] = highBank;
        mbcThings[3] = ramEnabled ? 1 : 0;
        mbcThings[4] = ramBank;
        mbcThings[5] = advancedBankingMode ? 1 : 0;
        SerializableMBC smbc = new SerializableMBC(MBCType.MBC1, mbcThings);

        return smbc;
    }

    @Override
    public AbstractMBC copy() {
        MBC1 mbcCopy = new MBC1(this.numberOfBanks);
        mbcCopy.romBank = this.romBank;
        mbcCopy.highBank = this.highBank;
        mbcCopy.ramEnabled = this.ramEnabled;
        mbcCopy.ramBank = this.ramBank;
        mbcCopy.advancedBankingMode = this.advancedBankingMode;
        return mbcCopy;
    }

    @Override
    public void write(int address, int value) {
        if (address <= 0x1FFF) {
            // RAM enable register
//            System.out.println("Writing 0x" + Integer.toHexString(address).toUpperCase() + " to RAM enable");
            if (value == 0xA) {
                ramEnabled = true;
            } else {
                ramEnabled = false;
            }
        } else if (address <= 0x3FFF) {
            // ROM bank number
            //System.out.println("switching to bank " + Integer.toHexString(value).toUpperCase());
            this.romBank = (value & 0x1F) % numberOfBanks;
        } else if (address <= 0x5FFF) {
            // RAM bank number --OR-- upper bits of ROM bank number
            if (numberOfBanks >= 64) this.highBank = value & 0x3;
            else this.ramBank = (value & 0x3);
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
}
