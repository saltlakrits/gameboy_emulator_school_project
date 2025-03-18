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

        FlatLightLaf.setup();

        // System.out.println("Hello, game boy!");

        if (args.length != 1 && args.length != 2) {
            System.out.println("You need to specify a ROM file, in one word, and nothing else, optionally with '-d' to enable showDebuggerAtStartup.");
            System.exit(-1);
        }

        // kinda dirty but does the trick i think
        String romFilePath = "";
        boolean showDebuggerAtStartup = false;
        if (args[0].equals("-d")) {
            showDebuggerAtStartup = true;
            if (args.length == 1) {
                System.out.println("Please specify a ROM file after the '-d' switch, in one word, and nothing else.");
                System.exit(-1);
            } else {
                romFilePath = args[1];
            }
        } else romFilePath = args[0];

        /*
        TODO

        Bug fixes for Ducktales, Tetris score, Gargoyle's Quest?

        Finish MBC1, make MBC3, saving

        Sound?
         */

        Display display = new Display(); // display is shared between ppu and frontend
        Memory memory = new Memory(romFilePath);
        Registers registers = new Registers();

        MemoryViewer memoryViewer = new MemoryViewer(memory, registers, showDebuggerAtStartup);

        CPU cpu = new CPU(memory, registers);
        cpu.setUpBoot();

        EmuViewer emuViewer = new EmuViewer(cpu, memory, display, memoryViewer);
        emuViewer.show();

        PPU ppu = new PPU(display, memory);

        while (true) {
                int dots = 0;
                long frameTime = System.currentTimeMillis() + 16;

                // run all the dots (T-cycles) for a single frame
                while (dots < DOTS_PER_FRAME) {
                    synchronized (cpu.lock()) {
                        int cycles;

                        ppuCycle(dots, ppu, memory);

                        if (memoryViewer.getEmulatorPaused() && memoryViewer.getShouldStep()) {
                            cycles = cpuCycle(cpu);
                            memoryViewer.postStepUpdate();
                        } else if (!memoryViewer.getEmulatorPaused()) {
                            cycles = cpuCycle(cpu);
                            // TODO Check that it finds everything!
                            memoryViewer.checkBreakpoints(cpu.getPC());
//                            memoryViewer.checkBreakpoints(cpu.getPC() - 1);
//                            memoryViewer.checkBreakpoints(cpu.getPC() + 1);
//                            memoryViewer.checkBreakpoints(cpu.getPC() + 2);
//                            memoryViewer.checkBreakpoints(cpu.getPC() + 2);
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
     * The dots passed in will range from 0 to roughly 70000, and the ppuCycle method should match the argument to
     * what the {@link PPU} is supposed to be working on in that moment. Liberties are taken, but the timing is
     * sufficient to play a lot of games.
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
        // TODO Rename or clarify checkFlags
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