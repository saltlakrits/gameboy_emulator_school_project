package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Registers;

import javax.swing.*;
import java.awt.*;

/**
 * Secondary (to the emulator screen) JFrame, which displays memory, disassembled ROM (and memory), and registers.
 */
public class MemoryViewer implements MBCListener
{

    private final JFrame frame = new JFrame("Memory Viewer");
    private final MemoryTable memoryTable;
    private final DisassemblyTable disassemblyTable;
    private final RegisterTable registerTable;

    private boolean emulatorPaused = false;
    private boolean shouldStep = false;

    private boolean bankSwitched = false;

    public MemoryViewer(Memory memory, Registers regs, boolean showDebugger) {
        memory.addMBCListener(this);

        this.memoryTable = new MemoryTable(memory);
        this.disassemblyTable = new DisassemblyTable(memory);
        this.registerTable = new RegisterTable(regs);

        this.emulatorPaused = showDebugger;

        create();

        frame.setVisible(showDebugger);
    }

    public void redisassemble() {
        this.disassemblyTable.redisassembleROM();
    }

    public void setEmulatorPaused(boolean emulatorPaused) {
        this.emulatorPaused = emulatorPaused;
    }

    public void create() {
//        memoryTableScrollPane.setViewportView(memoryTableScrollPane);
//        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        frame.setLayout(new GridLayout(2, 1));
        // add disassembly here

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.setPreferredSize(new Dimension(0, 0));
        topPanel.add(disassemblyTable.getDisassemblyTableScrollPane());
        topPanel.add(registerTable.getRegisterScrollPane());

        frame.add(topPanel);
        frame.add(memoryTable.getMemoryTableScrollPane());
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void toggleVisibility() {
        frame.setVisible(!frame.isVisible());
        this.emulatorPaused = frame.isVisible();
    }

    public boolean getShouldStep() {
        return this.shouldStep;
    }

    public void toggleDebugging() {
        this.emulatorPaused = !this.emulatorPaused;
    }

    public boolean getEmulatorPaused() {
        return this.emulatorPaused;
    }

    public void step() {
        this.shouldStep = true;
    }

    public void postStepUpdate() {
        this.shouldStep = false;
        if (bankSwitched) this.disassemblyTable.redisassembleROM();
        disassemblyTable.highlightRow(registerTable.getPC());
    }

    public void bankSwitched() {
        this.bankSwitched = true;
    }
}
