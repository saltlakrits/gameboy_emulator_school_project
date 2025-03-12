package se.liu.natho280.gbemu;

import javax.swing.*;
import java.awt.*;

/**
 * Secondary (to the emulator screen) JFrame, which displays memory, disassembled se.liu.natho280.GbEmu.ROM (and memory), and registers.
 */
public class MemoryViewer implements MBCListener {
    private final Memory memory;

    private final JFrame frame = new JFrame("se.liu.natho280.GbEmu.Memory Viewer");
    private final MemoryTable memoryTable;
    private final DisassemblyTable disassemblyTable;
    private final RegisterTable registerTable;

    private boolean showDebugger = false;
    private boolean emulatorPaused = false;
    private boolean shouldStep = false;

    private boolean bankSwitched = false;

    public MemoryViewer(Memory memory, Registers regs, boolean showDebugger) {
        this.memory = memory;
        memory.addMBCListener(this);

        this.memoryTable = new MemoryTable(memory);
        this.disassemblyTable = new DisassemblyTable(memory);
        this.registerTable = new RegisterTable(regs);

        this.showDebugger = showDebugger;
        this.emulatorPaused = showDebugger;

        create();

        frame.setVisible(showDebugger);
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
        this.showDebugger = frame.isVisible();
        this.emulatorPaused = frame.isVisible();
    }

    public boolean getShowDebugger() {
        return this.showDebugger;
    }

    public void setShowDebugger(boolean showDebugger) {
        this.showDebugger = showDebugger;
    }

    public boolean getStep() {
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
        if (bankSwitched) this.disassemblyTable.redisassembleBankSwitch();
        disassemblyTable.highlightRow(registerTable.getPC());
    }

    public void bankSwitched() {
        this.bankSwitched = true;
    }
}
