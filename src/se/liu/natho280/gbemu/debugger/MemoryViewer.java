package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Reg;
import se.liu.natho280.gbemu.cpu.Registers;

import java.util.List;
import java.util.ArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Secondary (to the emulator screen) JFrame, which displays memory, disassembled ROM (and memory), and registers.
 */
public class MemoryViewer implements MBCListener, RegisterListener {

    private final JFrame frame = new JFrame("Memory Viewer");
    private final MemoryTable memoryTable;
    private final DisassemblyTable disassemblyTable;
    private final RegisterTable registerTable;

    private boolean emulatorPaused = false;
    private boolean shouldStep = false;

    private boolean bankSwitched = false;

    private final List<DebuggerListener> debuggerListeners = new ArrayList<>();

    public MemoryViewer(Memory memory, Registers regs, boolean showDebugger) {
        memory.addMBCListener(this);
        regs.addRegisterListener(this);

        this.memoryTable = new MemoryTable(memory);
        this.disassemblyTable = new DisassemblyTable(memory);
        this.registerTable = new RegisterTable(regs);

        this.emulatorPaused = showDebugger;

        create();

        frame.setVisible(showDebugger);
    }

    public void addDebuggerListener(DebuggerListener listener) {
        this.debuggerListeners.add(listener);
    }

    private void notifyDebuggerListeners() {
        for (DebuggerListener listener : debuggerListeners) {
            listener.debuggerToggled();
        }
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

        // input
        JComponent pane = frame.getRootPane();
        final InputMap in = pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        in.put(KeyStroke.getKeyStroke("F2"), "toggle_debugging");
        in.put(KeyStroke.getKeyStroke("F3"), "step_forward");

        final ActionMap act = pane.getActionMap();
        act.put("toggle_debugging", new ToggleDebuggingAction());
        act.put("step_forward", new StepForwardAction());
    }

    public void toggleVisibility() {
        frame.setVisible(!frame.isVisible());
        this.emulatorPaused = frame.isVisible();

        if (this.emulatorPaused) {
            // upon pausing, update all registers in the frontend
            registerTable.updateAllRegisters();
        }
    }

    public boolean getShouldStep() {
        return this.shouldStep;
    }

    public void toggleDebugging() {
        this.emulatorPaused = !this.emulatorPaused;

        if (this.emulatorPaused) {
            // upon pausing, update all registers in the frontend
            registerTable.updateAllRegisters();
        }
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

    public void updateMemory() {
        if (!frame.isVisible()) return;
        memoryTable.updateTimersInDebugger();
    }

    public void bankSwitched() {
        this.bankSwitched = true;
    }

    @Override
    public void registerUpdated(final Reg reg) {
        if (this.emulatorPaused) {
            this.registerTable.registerUpdated(reg);
        }
    }

    private class ToggleDebuggingAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            toggleDebugging();
            notifyDebuggerListeners();
        }
    }

    private class StepForwardAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            if (!getEmulatorPaused()) new ToggleDebuggingAction().actionPerformed(e);
            step();
        }
    }
}
