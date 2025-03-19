package se.liu.natho280.gbemu.cpu;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.serialization.SerializableMBC;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;
import se.liu.natho280.gbemu.debugger.MBCListener;
import se.liu.natho280.gbemu.debugger.MemoryListener;
import se.liu.natho280.gbemu.ppu.OAM;
import se.liu.natho280.gbemu.rom.ROM;

import javax.swing.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * <p>This is the <a href=https://gbdev.io/pandocs/Memory_Map.html>memory</a> of the Game Boy. Input is stored here, as
 * well as all the regions of the memory. VRAM should not be accessible during certain parts of the PPU's cycle, but
 * that is not yet taken into account in this code. However, for future-proofing, the PPU can read the memory
 * with its own method, and the CPU uses the normal read() and write() methods for reads and writes that are
 * conditionally blocked.</p>
 *
 * <p>There are many hardware registers where reads and writes are intercepted and doing something different
 * than simply reading and writing to the array.</p>
 */
public class Memory implements Serializable
{
    // 0x0000 - 0x3FFF is the ROM (bank 00)
    // 0x4000 - 7FFF is the ROM (bank 1-NN)
    private ROM rom;

    // each location in memory is a byte
    // addresses: 0x0000 through 0xFFFF
    // ROM addresses removed from memory
    private UnsignedByte[] memory = new UnsignedByte[0x10000 - 0x8000];
    private int buttonByte = 0xFF;
    private int dpadByte = 0xFF;
    private int dmaTransferLock = 0;
    private boolean vramLocked = false;

    private double divTimer = 0.0;
    private int flooredDivTimer = 0;
    private int tima = 0;
    private int timaCycles = 0;

    private volatile boolean validROM = false;

    private transient List<MemoryListener> memoryListeners = new ArrayList<>();

    private transient List<MBCListener> mbcListeners = new ArrayList<>();

    public Memory() {
        for (int i = 0; i < memory.length; i++) {
            memory[i] = new UnsignedByte(0);
        }
    }

    public Memory(String romPath) {
        // create (& probably load) ROM with romPath string

        // catch exception
        loadROM(romPath);

        // FIXME some of these addresses may not be "real" memory, but just "magical addresses" that show e.g.
        //  status of hardware and similar (just like how some of the memory just point to the ROM in the cartridge!).
        //  As such, this may need to change!
        for (int i = 0; i < memory.length; i++) {
            memory[i] = new UnsignedByte(0);
        }
    }

    private void loadROM(String romPath) {
        try {
            this.rom = new ROM(romPath);
            this.validROM = true;
            for (MBCListener listener : mbcListeners) {
                this.rom.addMBCListener(listener);
            }
            CuteLogger.log(Level.INFO, "Successfully loaded ROM.");
        } catch (IllegalArgumentException e) {
            this.validROM = false;
            JOptionPane.showMessageDialog(null, e.getMessage() + "\n\nTip: Make sure the file you are trying to load is a Game Boy ROM-file!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * For ROM switching
     * @param newROM
     */
    public void reInitializeMemory(String newROM) {
        for (int i = 0; i < memory.length; i++) {
            unconditionalWrite(i + 0x8000, 0);
        }

        resetDivTimer();
        tima = 0;
        timaCycles = 0;

        loadROM(newROM);
    }

    /**
     * For ROM resetting
     */
    public void reInitializeMemory() {
        this.rom.reset();

        if (mbcListeners != null) {
            for (MBCListener l : mbcListeners) {
                this.rom.addMBCListener(l);
            }
        }
    }

    public boolean hasValidROM() {
        return validROM;
    }

    public void restoreState(SerializationWrapper serializationWrapper) {
        Memory wrapperMemory = serializationWrapper.getMemory();
        this.memory = wrapperMemory.memory;
        this.buttonByte = wrapperMemory.buttonByte;
        this.dpadByte = wrapperMemory.dpadByte;
        this.dmaTransferLock = wrapperMemory.dmaTransferLock;
        this.vramLocked = wrapperMemory.vramLocked;
        this.divTimer = wrapperMemory.divTimer;
        this.flooredDivTimer = wrapperMemory.flooredDivTimer;
        this.tima = wrapperMemory.tima;
        this.timaCycles = wrapperMemory.timaCycles;
        this.validROM = wrapperMemory.validROM;

        this.rom.restoreState(serializationWrapper, mbcListeners);
    }

    public SerializableMBC getSerializableMBC() {
        return this.rom.getSerializableMBC();
    }

    public ROM getROM() {
        return this.rom.copy();
    }

    public void resetDivTimer() {
        divTimer = 0.0;
        flooredDivTimer = 0;
    }

    public void incDivTimer(double value) {
        divTimer = (divTimer + value) % 256;
        flooredDivTimer = ((int)divTimer) & 0xFF;

        memory[0xFF04 - 0x8000].set(flooredDivTimer);
    }

    public void incTimaCycles(int cycles) {
        int timerControl = unconditionalRead(0xFF07); // TAC register
        if ((timerControl & 0x4) == 0) return; // if bit 3 is off, TIMA isn't incremented

        int mod = 0;
        switch (timerControl & 0x3) {
            case 0:
                mod = 256;
                break;
            case 1:
                mod = 4;
                break;
            case 2:
                mod = 16;
                break;
            case 3:
                mod = 64;
                break;
        }

        timaCycles += cycles;
        if (timaCycles >= mod) {
            // inc timer...
            tima += 1;
            // reset timaCycles
            timaCycles -= mod;
        }
        if (tima > 0xFF) {
            // if tima should overflow, we overflow it and set it to TMA (Timer Modulo)
//            tima = memory[0xFF06].get();
            tima = unconditionalRead(0xFF06); //  TMA read
            // raise TIMA interrupt -> Bit 2 of FF0F
//            memory[0xFF0F].set(memory[0xFF0F].get() | (1 << 2));
            // set interrupt bit
            unconditionalWrite(0xFF0F, unconditionalRead(0xFF0F) | (1 << 2)); // interrupt (this will happen quite a bit)
//            memory[0xFF0F - 0x8000].set(unconditionalRead(0xFF0F) | (1 << 2));
        }
//        memory[0xFF05].set(tima);
//        unconditionalWrite(0xFF05, tima);
        memory[0xFF05 - 0x8000].set(tima);
    }

    public void subDmaTransferLock(int dots) {
        if (dmaTransferLock > 0) dmaTransferLock -= dots;
        if (dmaTransferLock < 0) dmaTransferLock = 0;
    }

    public void lockVram() {
        vramLocked = true;
    }

    public void unlockVram() {
        vramLocked = false;
    }

    public void write(int address, int value) {
        int subtractedAddress = address - 0x8000;

        if (address <= 0x7FFF) {
            rom.write(address, value);
        } else if (address >= 0xA000 && address <= 0xBFFF) {
            rom.write(address, value); // writing to RAM a separate method?
        } else if (address == 0xFF04) {
            resetDivTimer();
            //if (address == 0xFF01) System.out.print((char)value); // print serial port writes
        } else if (address == 0xFF46) {
            // copy 0x9F objects from WRAM to the object memory area
            // takes 160 cycles = 640 dots
            // value will in this case be start address / 0x100
            for (int i = 0; i <= 0x9F; i++) {
                // For each spot in memory between 0xXX00-0xXX9F, copy it to the object memory
                // which is 0xFE00-0xFE9F
//                memory[0xFE00 + i] = memory[(value * 0x100) | i];
//                unconditionalWrite(0xFE00 + i, unconditionalRead(value * 0x100 + i));
                int newVal = memory[(value * 0x100 + i - 0x8000)].get();
                memory[0xFE00 - 0x8000 + i].set(newVal);
            }
            // lock all read/write for 640 dots
            dmaTransferLock = 640;
        } else if (address <= 0x9FFF) {
//            if (!vramLocked) memory[address] = new UnsignedByte(value); // FIXME
//            if (!vramLocked) unconditionalWrite(address, value);
            if (!vramLocked) memory[subtractedAddress].set(value);
        } else {
//            if (dmaTransferLock == 0) memory[address] = new UnsignedByte(value); // FIXME
//            if (dmaTransferLock == 0) unconditionalWrite(address, value);
            if (dmaTransferLock == 0) memory[subtractedAddress].set(value);
            //memory[subtractedAddress].set(value);
        }

        for (MemoryListener memoryListener : memoryListeners) {
            memoryListener.memoryChanged(address);
        }
    }

    public int read(int address) {
        if (address >= 0x0 && address <= 0x7FFF) {
            return rom.get(address);
        }
        if (address >= 0xA000 && address <= 0xBFFF) {
            // reading from RAM must be redirected through MBC
            return rom.get(address);
        }
        if (address == 0xFF00) {
            // input register!
            // depending on bit 4 and bit 5, we should return different input registers
//            int input = (memory[0xFF00].get() & 0x30) >> 4; // FIXME
            int input = (unconditionalRead(0xFF00) & 0x30) >> 4;
            if ((input & 0x1) == 0) {
                return dpadByte & 0xF;
            } else if ((input & 0x2) == 0) {
                return buttonByte & 0xF;
            } else {
                return 0xF;
            }
        }
        if (address >= 0x8000 && address <= 0x9FFF) {
//            if (!vramLocked) return memory[address].get();
//            else return 0xFF;
            if (!vramLocked) return unconditionalRead(address);
            else return 0xFF;
        }
        // maybe need to add other regions as well
//        return memory[address].get(); // FIXME
        return unconditionalRead(address);
    }

    /**
     * For the PPU, and various other classes to use. The PPU & the others are never
     * locked out of reading.
     * @param address
     * @return
     */
    public int unconditionalRead(int address) {
        if (address >= 0x0 && address <= 0x7FFF) {
            if (this.rom == null) return 0;
            return rom.get(address);
        }
        return memory[address - 0x8000].get();
    }

    /**
     * For the PPU, and various other classes to use. The PPU & the others are never
     * locked out of writing.
     * @param address
     * @param value
     */
    public void unconditionalWrite(int address, int value) {
        if (address >= 0x0 && address <= 0x7FFF) {
            rom.write(address, value);
            // FIXME no listener call -- is that fine?
            return;
        }

//        memory[address - 0x8000] = new UnsignedByte(value);
        memory[address - 0x8000].set(value);

        for (MemoryListener memoryListener : memoryListeners) {
            memoryListener.memoryChanged(address);
        }
    }

    public OAM[] getOAMs() {
        OAM[] oams = new OAM[40];
        for (int i = 0; i < (oams.length * 4); i += 4) {
//            int y = memory[0xFE00 + i].get(); // FIXME
            int y = unconditionalRead(0xFE00 + i);
//            int x = memory[0xFE00 + (i + 1)].get(); // FIXME
            int x = unconditionalRead(0xFE00 + (i + 1));
//            int index = memory[0xFE00 + (i + 2)].get(); // FIXME
            int index = unconditionalRead(0xFE00 + (i + 2));
//            int flags = memory[0xFE00 + (i + 3)].get(); // FIXME
            int flags = unconditionalRead(0xFE00 + (i + 3));
            OAM newOAM = new OAM(y, x, index, flags);
            oams[i / 4] = newOAM;
        }
        return oams;
    }

    /**
     * Releasing a button means un-setting (1'ing, unintuitively) the bit in the appropriate byte.
     * Also calls {@link Memory#setInputInterrupt}.
     * @param b a button to release
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public void releaseButton(GameButton b) {

        switch (b) {
            case A, B, START, SELECT -> buttonByte |= (1 << GameButton.buttonToBit(b));
            case UP, DOWN, LEFT, RIGHT -> dpadByte |= (1 << GameButton.buttonToBit(b));
        }

        switch (b) {
            case START, SELECT, A, B -> setInputInterrupt(false, false);
            case DOWN, UP, LEFT, RIGHT -> setInputInterrupt(true, false);
        }
    }

    /**
     * Setting a button means 0'ing (yes, 0'ing) the bit in the appropriate byte.
     * Also calls {@link Memory#setInputInterrupt}.
     * @param b a button to press
     * @see <a href=https://gbdev.io/pandocs/Joypad_Input.html>Pan Docs - Joypad Input</a>
     */
    public void setButton(GameButton b) {

        switch (b) {
            case A, B, START, SELECT -> buttonByte &= ((1 << GameButton.buttonToBit(b)) ^ 0xFF);
            case UP, DOWN, LEFT, RIGHT -> dpadByte &= ((1 << GameButton.buttonToBit(b)) ^ 0xFF);
        }

        switch (b) {
            case START, SELECT, A, B -> setInputInterrupt(false, true);
            case DOWN, UP, LEFT, RIGHT -> setInputInterrupt(true, true);
        }
    }

    /**
     * If certain conditions apply, we should request an input {@link Interrupt} when buttons are pressed.
     * Handled in {@link CPU#checkInterrupts}.
     * @param dpad
     * @param setToHigh
     * @see <a href=https://gbdev.io/pandocs/Interrupt_Sources.html#int-60--joypad-interrupt>Pan Docs - Joypad Interrupt</a>
     */
    private void setInputInterrupt(boolean dpad, boolean setToHigh) {
//        int interruptFlags = memory[0xFFFF].get(); // FIXME
        int interruptFlags = unconditionalRead(0xFFFF);
        boolean inputInterruptsEnabled = (interruptFlags & (1 << 4)) != 0;
//        int newInterruptByte = memory[0xFF0F].get(); // FIXME
        int newInterruptByte = unconditionalRead(0xFF0F);

        if (!inputInterruptsEnabled) {
            // unset the interrupt if they aren't enabled, and do an early return
            newInterruptByte &= interruptFlags;
//            memory[0xFF0F].set(newInterruptByte); // FIXME
            unconditionalWrite(0xFF0F, newInterruptByte);
            return;
        }
        // if dpad -> bit 4, else action buttons -> bit 5
        int bit = (dpad ? 4 : 5);
        // mask out the old interrupt bit
        newInterruptByte &= ((1 << 4) ^ 0xFF);

        // or in the byte
//        if ((memory[0xFF00].get() & (1 << bit)) == 0) { // FIXME
//            memory[0xFF0F].set(newInterruptByte | ((setToHigh ? 1 : 0) << 4)); // FIXME
//        }
        if ((unconditionalRead(0xFF00) & (1 << bit)) == 0) {
            unconditionalWrite(0xFF0F, newInterruptByte | ((setToHigh ? 1 : 0) << 4));
        }
    }

    public void addMemoryListener(MemoryListener memoryListener) {
        this.memoryListeners.add(memoryListener);
    }

    public void addMBCListener(MBCListener l) {
        this.mbcListeners.add(l);
        if (this.rom != null) rom.addMBCListener(l);
    }

    private List<MBCListener> reInitialize() {
        // zero out the memory
        for (int i = 0; i < memory.length; i++) {
            unconditionalWrite(i + 0x8000, 0);
        }

        resetDivTimer();
        tima = 0;
        timaCycles = 0;

        // drop ROM, reinit
        return rom.getMBCListeners();
    }
}
