package se.liu.natho280.gbemu;

import com.formdev.flatlaf.FlatLightLaf;
import se.liu.natho280.gbemu.cpu.CPU;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Registers;
import se.liu.natho280.gbemu.debugger.MemoryViewer;
import se.liu.natho280.gbemu.gui.EmuViewer;
import se.liu.natho280.gbemu.ppu.Display;
import se.liu.natho280.gbemu.ppu.PPU;

/**
 * Entry point. Sets up the different parts of the emulator, including frontend, and starts a loop of running {@link CPU} and
 * {@link PPU} (Picture Processing Unit, graphics) cycles.
 */
public class Main {
    private static final double DOTS_PER_SECOND = 4.1943 * 1_000_000;
    // 59.73 frames per second
    private static final double FRAMES_PER_SECOND = 59.73;
    // about 70220.99... so add 1.
    private static final int DOTS_PER_FRAME = (int)(DOTS_PER_SECOND / FRAMES_PER_SECOND) + 1;


//    private static final long MILLIS_PER_FRAME = (long)((1.0 / 59.73) * 1000);

    public static void main(String[] args) {

        // FlatLaf is just a look-and-feel that looks prettier than the default. Nothing *really* depends on this library, and this is
        // effectively the only time it is mentioned in the project. Will make all swing components look modern & uniform.
        FlatLightLaf.setup();

        Display display = new Display(); // display is shared between ppu and frontend
        Memory memory = new Memory();
        Registers registers = new Registers();

        MemoryViewer memoryViewer = new MemoryViewer(memory, registers);

        CPU cpu = new CPU(memory, registers);
        cpu.setUpBoot();

        EmuViewer emuViewer = new EmuViewer(cpu, memory, display, memoryViewer);
        emuViewer.show();

        PPU ppu = new PPU(display, memory);

        while (true) {
            if (memory.hasValidROM()) {
                int dots = 0;
                long frameTime = System.currentTimeMillis() + 16;

                // run all the dots (T-cycles) for a single frame
                while (dots < DOTS_PER_FRAME && memory.hasValidROM()) {
                    synchronized (cpu.lock()) {
                        int cycles;

                        ppuCycle(dots, ppu, memory);

                        if (memoryViewer.getEmulatorPaused() && memoryViewer.getShouldStep()) {
                            cycles = cpuCycle(cpu);
                            memoryViewer.postStepUpdate();
                        } else if (!memoryViewer.getEmulatorPaused()) {
                            cycles = cpuCycle(cpu);
                            memoryViewer.checkBreakpoints(cpu.getPC());
                        } else {
                            cycles = 0;
                        }

                        memory.subDmaTransferLock(cycles * 4);
                        dots += cycles * 4;
                    }
                }

                memoryViewer.updateMemory();

                while (frameTime > System.currentTimeMillis()) {
                    // wait until new frame -- this is what limits the emulator speed and
                    // should be where the program spends the majority of it's runtime
                }
            }
        }
    }

    /**
     * Runs a CPU cycle if appropriate (not halted).
     * @param cpu
     * @return
     */
    private static int cpuCycle(CPU cpu) {
        int cycles;
        if (!cpu.getHalted()) {
            // returns cycles, multiply by 4 to get dots
            cycles = cpu.runCycle();
        } else {
            cycles = 1;
        }
        cpu.updateTimers(cycles);

        return cycles;
    }

    /**
     * <p>The dots passed in will range from 0 to roughly 70000, and the ppuCycle method should match the argument to
     * what the {@link PPU} is supposed to be working on in that moment. Liberties are taken, but the timing is
     * sufficient to play a lot of games.</p>
     * <p>The VRAM should be locked in different spots, but implementing this behavior REQUIRES very careful timing
     * that we do not currently have.</p>
     * @param dots the current dot (T-cycle) count of the frame
     */
    public static void ppuCycle(int dots, PPU ppu, Memory memory) {
        int ly = dots / 456; // LY, y coordinate
        int modDots = dots % 456; // 456 dots per scanline

        if (ly != memory.unconditionalRead(0xFF44)) {
            // if LY changed, we want to reset the flags so the different parts of the scanline is drawn again
            ppu.resetFlags();
            // Update LY register if LY changed
            memory.unconditionalWrite(0xFF44, ly);
        }

        // the FIRST time we get to vblank, ppu.flags will be non-nil, so we use that to detect reaching vblank
        // we should set vblank interrupt and STAT
        if (ly == 143 && ppu.checkFlags() && !ppu.getFlag(3)) {
            // vblank interrupt!
            ppu.vblank();
        } else if (ly > 143) {
            return; // vblank
        }
        if (modDots < 80 && !ppu.getFlag(0)) {
            // Potentially: LOCK OAM BESIDE DMA TRANSFER
            // scanning oam
            ppu.oamScan(ly);
            return;
        }
        if (modDots < 252 && !ppu.getFlag(1)) {
            // Potentially: UNLOCK OAM
            // mode 3, drawing scanline
            // LOCK VRAM
//            memory.lockVram(); // implementing the VRAM lock needs very, very careful attention to timing
            ppu.mode3(ly);
            return;
        }
        if (modDots < 455 && !ppu.getFlag(2)) {
            // mode 0, hblank

            // UNLOCK VRAM
//            memory.unlockVram(); // implementing the VRAM lock needs very, very careful attention to timing
            ppu.hblank();
        }
    }
}