package se.liu.natho280.gbemu;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;

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

    private static final long MILLIS_PER_FRAME = (long)((1. / 59.73) * 1000);

    private static CPU cpu = null;
    private static PPU ppu = null;
    private static Memory memory = null;
    private static Registers registers = null;

    public static void main(String[] args) {
        System.out.println("Hello, game boy!");

        if (args.length != 1 && args.length != 2) {
            System.out.println("You need to specify a se.liu.natho280.GbEmu.ROM file, in one word, and nothing else, optionally with '-d' to enable showDebuggerAtStartup.");
            System.exit(-1);
        }

        // kinda dirty but does the trick i think
        String romPath = "";
        boolean showDebuggerAtStartup = false;
        if (args[0].equals("-d")) {
            showDebuggerAtStartup = true;
            if (args.length == 1) {
                System.out.println("Please specify a se.liu.natho280.GbEmu.ROM file after the '-d' switch, in one word, and nothing else.");
                System.exit(-1);
            } else {
                romPath = args[1];
            }
        } else romPath = args[0];

        /*
        TODO

        DONE Need to do a full rewrite of the timing. System.nanoTime() only guarantees millisecond accuracy,
        so it is unreliable. The threaded approach doesn't work, we need to COUNT CYCLES.
        It isn't that bad to do, but it will require a lot of restructuring and thinking...

        Finish interrupts (at least the bare minimum)
            -- what i wrote so far has *some* guesswork in it, as the docs were rather ambiguous on some points
            -- SKIP SERIAL interrupt (this is for link cable, we don't support that anyway)
        DONE RETI instruction (return from interrupt)

        THERE IS NO CHANCE WE ARE ACTUALLY DONE WITH THE se.liu.natho280.GbEmu.PPU YET, we just got simple backgrounds to work.

        DONE Implement timer?

        Input!
        -- receive it in UI
        -- write it
        -- interrupts

        The se.liu.natho280.GbEmu.Memory/SharedMemory classes are in desperate need of cleanup, they are held together by duct tape & a prayer

        Implement MBCs (mapper, switching between memory banks on the se.liu.natho280.GbEmu.ROM)
            -- if we start with only one mbc, we might be able to track down roms requiring that specific mbc and play them

        Sound?

        se.liu.natho280.GbEmu.Memory viewer?

        skip boot rom? (i.e. just hardcode the default palette, etc, that the boot rom sets)

        Disassembler...? simple version. This will be tedious as fuck, but not strictly hard, I don't think...
         */

        Display display = new Display(); // display is shared between se.liu.natho280.GbEmu.PPU and frontend
        memory = new Memory(romPath);
        registers = new Registers();

        MemoryViewer memoryViewer = new MemoryViewer(memory, registers, showDebuggerAtStartup);

        cpu = new CPU(args[0], memory, registers);
        cpu.setupBoot();

        EmuViewer emuViewer = new EmuViewer(memory, display, memoryViewer);
        emuViewer.show();

        ppu = new PPU(display, memory);

        int dots = 0;
        long frameTime;

        int cycles;

        try (FileOutputStream fos = new FileOutputStream("log.txt")) {
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            while (true) {
                frameTime = System.currentTimeMillis() + 16;
                dots = 0;

                // run all the dots (T-cycles) for a single frame
                while (dots < DOTS_PER_FRAME) {
                    ppuCycle(dots);

                    if (memoryViewer.getEmulatorPaused() && memoryViewer.getStep()) {
                        cycles = cpuCycle(bos);
                        memoryViewer.postStepUpdate();
                    } else if (!memoryViewer.getEmulatorPaused()) {
                        cycles = cpuCycle(bos);
                    } else {
                        cycles = 0;
                    }

                    memory.subDmaTransferLock(cycles * 4);
                    dots += cycles * 4;
                }
                // wait until new frame -- this is what limits the emulator speed and should be where the program
                // spends the majority of the time.
                while (frameTime > System.currentTimeMillis());
            }
        } catch (Exception e) {
            e.printStackTrace(); // can replace with "more robust logging"
        }
    }

    private static int cpuCycle(BufferedOutputStream bos) {
        int cycles;
        if (!cpu.getHalted()) {
            // returns cycles, multiply by 4 to get dots
            cycles = cpu.runCycle(bos);
        } else {
            cycles = 1;
        }
        cpu.updateTimers(cycles);
//        dots += 4 * cycles;

        return cycles;
    }

    /**
     * The dots passed in will range from 0 to roughly 70000, and the ppuCycle method should match the argument to
     * what the {@link PPU} is supposed to be working on in that moment. Liberties are taken, but the timing is
     * sufficient to play a lot of games.
     * @param dots the current dot (T-cycle) count of the frame
     */
    public static void ppuCycle(int dots) {
        int ly = dots / 456; // LY, y coordinate
        int modDots = dots % 456; // 339 dots per scanline

        if (ly != memory.unconditionalRead(0xFF44)) {
            // if LY changed, we want to reset the flags so the different parts of the scanline is drawn again
            ppu.resetFlags();
            // Update LY register if LY changed
            memory.unconditionalWrite(0xFF44, ly);
        }

        // the FIRST time we get to vblank, ppu.flags will be non-nil, so we use that to detect reaching vblank
        // we should set vblank interrupt and STAT
        if (ly == 143 && ppu.anyFlag()) {
            // vblank interrupt!
            ppu.vblank();
        }
        if (ly > 143) {
            return; // vblank
        }
        if (modDots < 80 && !ppu.getFlag(0)) {
            // Potentially: LOCK se.liu.natho280.GbEmu.OAM BESIDE DMA TRANSFER
            // scanning oam
            ppu.oamScan(ly);
            return;
        }
        if (modDots < 252 && !ppu.getFlag(1)) {
            // Potentially: UNLOCK se.liu.natho280.GbEmu.OAM
            // mode 3, drawing scanline
            // LOCK VRAM
//            memory.lockVram(); // implementing the VRAM lock needs very, very careful attention to timing
            ppu.mode3(ly);
            //if (ppu.getFlag(1)) ppu.setDots(252); // this flag could have been changed by mode3
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