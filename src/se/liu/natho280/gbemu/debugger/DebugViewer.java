package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Reg;
import se.liu.natho280.gbemu.cpu.Registers;

import java.util.List;
import java.util.ArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;

/**
 * Secondary (to the emulator screen) JFrame, which displays memory, disassembled ROM (and memory), and registers.
 */
public class DebugViewer implements MBCListener, RegisterListener {

    private final JFrame frame = new JFrame("Debugger");
    private final MemoryTable memoryTable;
    private final DisassemblyTable disassemblyTable;
    private final RegisterTable registerTable;
    private final BreakpointTable breakpointTable;

    private boolean emulatorPaused = false;
    private boolean shouldStep = false;

    private boolean bankSwitched = false;

    private final List<DebuggerListener> debuggerListeners = new ArrayList<>();

    private final List<Integer> breakpoints = new ArrayList<>();

    public DebugViewer(Memory memory, Registers regs) {
        memory.addMBCListener(this);
        regs.addRegisterListener(this);

        this.memoryTable = new MemoryTable(memory);
        this.disassemblyTable = new DisassemblyTable(memory);
        this.registerTable = new RegisterTable(regs);
        this.breakpointTable = new BreakpointTable();

        create();

        frame.setVisible(false);
    }

    /**
     * Adds a breakpoint to both the internal list and the UI
     */
    public void addBreakpoint() {
        try {
            int newBreakpointAddress = Integer.parseInt(JOptionPane.showInputDialog(frame, "Address for new breakpoint:", "Add Breakpoint", JOptionPane.PLAIN_MESSAGE), 16);
            breakpoints.add(newBreakpointAddress);
            this.breakpointTable.addBreakpoint(newBreakpointAddress);
        } catch (NumberFormatException ex) {
            System.err.println(ex.getMessage());
            CuteLogger.log(Level.WARNING, ex.getMessage());
        }
    }

    /**
     * Removes a breakpoint from both the internal list and the UI
     */
    public void removeBreakpointIndex() {
        try {
            int index = Integer.valueOf((String)JOptionPane.showInputDialog(frame, "Index to remove:", "Remove Breakpoint", JOptionPane.PLAIN_MESSAGE, null, null, getBreakpoints().size())) - 1;
            breakpoints.remove(index);
            this.breakpointTable.removeBreakpoint(index);
        } catch (NumberFormatException ex) {
            CuteLogger.log(Level.WARNING, ex.getMessage());
        }
    }

    /**
     * Removes all breakpoints from the internal list and the UI
     */
    public void clearBreakpoints() {
        breakpoints.clear();
        breakpointTable.clear();
    }

    public List<Integer> getBreakpoints() {
        return new ArrayList<>(breakpoints);
    }

    /**
     * Checks if the breakpoint list contains the passed in address. Used to determine whether a breakpoint has been reached and
     * emulation should be halted.
     * @param address
     */
    public void checkBreakpoints(int address) {
        if (!breakpoints.isEmpty() && breakpoints.contains(address)) {
            new ToggleDebuggingAction().actionPerformed(null);
        }
    }

    public void addDebuggerListener(DebuggerListener listener) {
        this.debuggerListeners.add(listener);
    }

    private void notifyDebuggerToggled() {
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

    /**
     * Helper method for constructor.
     */
    private void create() {
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        makeBar();

        frame.setLayout(new GridLayout(2, 1));

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.setPreferredSize(new Dimension(0, 0));
        topPanel.add(disassemblyTable.getDisassemblyTableScrollPane());

        JPanel topRightPanel = new JPanel(new GridLayout(2, 1));

        topRightPanel.add(registerTable.getRegisterScrollPane());
        topRightPanel.add(breakpointTable.getBreakpointTableScrollPane());

        topPanel.add(topRightPanel);

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

    /**
     * Make a menu bar and add it to the frame
     */
    private void makeBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem changeROMItem = new JMenuItem("Change ROM...");
        changeROMItem.addActionListener(new ChangeROMAction());
        fileMenu.add(changeROMItem);

        JMenuItem saveStateItem = new JMenuItem("Save state...");
        saveStateItem.addActionListener(new SaveStateAction());
        fileMenu.add(saveStateItem);

        JMenuItem loadStateItem = new JMenuItem("Load state...");
        loadStateItem.addActionListener(new LoadStateAction());
        fileMenu.add(loadStateItem);

        fileMenu.addSeparator();

        JMenuItem closeItem = new JMenuItem("Close debugger");
        closeItem.addActionListener(new HideDebuggerAction());
        fileMenu.add(closeItem);

        menuBar.add(fileMenu);

        JMenu debugMenu = new JMenu("Debug");
        JMenuItem pauseItem = new JMenuItem("Toggle pause");
        pauseItem.addActionListener(new ToggleDebuggingAction());
        debugMenu.add(pauseItem);

        JMenuItem stepItem = new JMenuItem("Step forward");
        stepItem.addActionListener(new StepForwardAction());
        debugMenu.add(stepItem);

        debugMenu.addSeparator();

        JMenuItem addBreakpointItem = new JMenuItem("Add breakpoint...");
        addBreakpointItem.addActionListener(new AddBreakpointAction());
        debugMenu.add(addBreakpointItem);

        JMenuItem removeBreakpointItem = new JMenuItem("Remove breakpoint...");
        removeBreakpointItem.addActionListener(new RemoveBreakpointAction());
        debugMenu.add(removeBreakpointItem);

        JMenuItem clearBreakpointsItem = new JMenuItem("Clear breakpoints");
        clearBreakpointsItem.addActionListener(new ClearBreakpointsAction());
        debugMenu.add(clearBreakpointsItem);

        debugMenu.addSeparator();

        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.addActionListener(new ResetROMAction());
        debugMenu.add(resetItem);

        menuBar.add(debugMenu);

        frame.setJMenuBar(menuBar);
    }

    /**
     * Show/hide the window without disposing it. Also pauses/resumes emulation appropriately.
     */
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

    /**
     * Toggle pause for emulation.
     */
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

    /**
     * After a step (and some other edge case situations) we should update the debugging view. This method handles that.
     */
    public void postStepUpdate() {
        this.shouldStep = false;
        if (bankSwitched) this.disassemblyTable.redisassembleROM();
        disassemblyTable.highlightRow(registerTable.getPC());
    }

    public void updateMemory() {
        if (!frame.isVisible()) return;
        memoryTable.updateTimersInDebugger();
    }

    /**
     * When the ROM bank switches, we need to redisassemble the ROM to make it reflect what is currently "visible" to the Game Boy.
     * This method sets a flag that is checked after steps, since we don't want to redisassemble the ROM each step unconditionally.
     */
    public void bankSwitched() {
        this.bankSwitched = true;
    }

    @Override
    public void registerUpdated(final Reg reg) {
        if (this.emulatorPaused) {
            this.registerTable.registerUpdated(reg);
        }
    }

    public void forceRedisassemble() {
        this.disassemblyTable.redisassembleROM();
    }

    private class HideDebuggerAction extends AbstractAction {
        @Override public void actionPerformed(final ActionEvent e) {
            frame.setVisible(!frame.isVisible());
            if (getEmulatorPaused()) setEmulatorPaused(false);
        }
    }

    private class ChangeROMAction extends AbstractAction {
        @Override public void actionPerformed(final ActionEvent e) {
            for (DebuggerListener l : debuggerListeners) {
                l.changeROM();
            }
        }
    }

    private class SaveStateAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            for (DebuggerListener l : debuggerListeners) {
                l.saveState();
            }
        }
    }

    private class LoadStateAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            for (DebuggerListener l : debuggerListeners) {
                l.loadState();
            }
        }
    }

    private class ToggleDebuggingAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            toggleDebugging();
            notifyDebuggerToggled();
        }
    }

    private class StepForwardAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            if (!getEmulatorPaused()) new ToggleDebuggingAction().actionPerformed(e);
            step();
        }
    }

    private class ResetROMAction extends AbstractAction {
        @Override public void actionPerformed(final ActionEvent e) {
            for (DebuggerListener l : debuggerListeners) {
                l.resetROM();
            }
        }
    }

    private class AddBreakpointAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            addBreakpoint();
        }
    }

    private class RemoveBreakpointAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            removeBreakpointIndex();
        }
    }

    private class ClearBreakpointsAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            clearBreakpoints();
        }
    }
}
