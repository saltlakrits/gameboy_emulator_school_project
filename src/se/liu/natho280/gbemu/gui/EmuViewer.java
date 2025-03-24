package se.liu.natho280.gbemu.gui;

import se.liu.natho280.gbemu.CuteLogger;
import se.liu.natho280.gbemu.cpu.Reg;
import se.liu.natho280.gbemu.debugger.DebuggerListener;
import se.liu.natho280.gbemu.serialization.SerializationWrapper;
import se.liu.natho280.gbemu.cpu.CPU;
import se.liu.natho280.gbemu.cpu.GameButton;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Registers;
import se.liu.natho280.gbemu.debugger.DebugViewer;
import se.liu.natho280.gbemu.ppu.Display;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.HexFormat;
import java.util.logging.Level;

/**
 * The frontend window that displays the emulator screen and receives the input from the user.
 */
public class EmuViewer extends WindowAdapter implements DebuggerListener, ComponentListener
{
    // Frontend for the emulator

    private final CPU cpu;
    private final Memory memory;
    private final JFrame frame = new JFrame("gbEmu (load a ROM)");
    private final JPopupMenu popupMenu = new JPopupMenu();
    private final DebugViewer debugViewer;
    private final DisplayComponent displayComponent;

    public EmuViewer(CPU cpu, Memory memory, Display display, DebugViewer debugViewer) {
        this.cpu = cpu;
        this.memory = memory;
	this.debugViewer = debugViewer;
        debugViewer.addDebuggerListener(this);
        displayComponent = new DisplayComponent(display);
        frame.addComponentListener(this);
        frame.addWindowListener(this);
        frame.getContentPane().setBackground(Color.BLACK);
    }

    private void saveRAM() {
        // save the RAM memory
        this.memory.saveRAM();
    }

    /**
     * When we change the ROM through the UI, we need to reinitialize
     * the CPU and Memory. The PPU shouldn't care that we do so.
     * @param newROM path to new ROM file
     */
    public void changeROM() {
        // default to home folder on Linux, My Documents or such on Windows, probably something similar on OSX
        JFileChooser fc = new JFileChooser();
        // reasonable size on large screens, not too big for tiny screens
        fc.setPreferredSize(new Dimension(800, 600));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Load ROM");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileFilter(new FileNameExtensionFilter(".gb files", "gb"));

        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            String romPath = fc.getSelectedFile().getAbsolutePath();

            synchronized (cpu.lock()) {
                this.memory.reInitializeMemory(romPath); // reinitialize memory (zero it out), inside it reInit ROM as well?
                this.cpu.reInitializeCPU(); // reinit registers? anything else? run setUpBoot() again
                this.debugViewer.redisassemble();
            }

            // TODO Don't set this title if loading of ROM failed
            frame.setTitle("gbEmu");
            debugViewer.clearBreakpoints();
        }
    }

    /**
     * For game resetting. Reinitializes the memory and the debugging UI, and basically sets the emulator back to the initial state it has
     * upon initially loading a game.
     */
    public void resetROM() {
        synchronized (cpu.lock()) {
            this.memory.reInitializeMemory(); // reinitialize memory (zero it out), inside it reInit ROM as well?
            this.cpu.reInitializeCPU(); // reinit registers? anything else? run setUpBoot() again
            this.debugViewer.redisassemble();
        }
        if (!debugViewer.getEmulatorPaused()) {
            debugViewer.toggleDebugging();
            debuggerToggled();
        }
    }

    /**
     * Used to recover the saved state from a file
     */
    public void loadState() {
        // default to home folder on Linux, My Documents or such on Windows, probably something similar on OSX
        JFileChooser fc = new JFileChooser();
        // reasonable size on large screens, not too big for tiny screens
        fc.setPreferredSize(new Dimension(800, 600));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Load State");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileFilter(new FileNameExtensionFilter(".state files", "state"));

            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                String loadPath = fc.getSelectedFile().getAbsolutePath();

                synchronized (cpu.lock()) {
                    SerializationWrapper serializationWrapper = new SerializationWrapper(loadPath);

                    try {
                        this.memory.restoreState(serializationWrapper);
                    } catch (IllegalStateException e) {
                        CuteLogger.log(Level.SEVERE, "Failed to restore Memory when loading save state: " + e.getMessage());
                        return;
                    }
                    this.cpu.restoreState(serializationWrapper);
                }

                frame.setTitle("gbEmu");
                debugViewer.forceRedisassemble();
            }
    }

    /**
     * Used to save the emulator state to a file
     */
    public void saveState() {
        // default to home folder on Linux, My Documents or such on Windows, probably something similar on OSX
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("myState.state"));
        // reasonable size on large screens, not too big for tiny screens
        fc.setPreferredSize(new Dimension(800, 600));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Save State");
        fc.setDialogType(JFileChooser.SAVE_DIALOG);
        fc.setFileFilter(new FileNameExtensionFilter(".state files", "state"));

        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String savePath = fc.getSelectedFile().getAbsolutePath();
            if (!savePath.endsWith(".state")) {
                savePath += ".state";
            }

            synchronized (cpu.lock()) {
                SerializationWrapper sw = new SerializationWrapper(this.cpu, this.memory);
                sw.serialize(savePath);
            }
        }
    }

    public void addBreakpoint() {
        debugViewer.addBreakpoint();
    }

    public void removeBreakpoint() {
        debugViewer.removeBreakpointIndex();
    }

    private void logRegs(Registers regs) {
        StringBuilder sb = new StringBuilder();
        HexFormat hex = HexFormat.of();

        sb.append("AF: $").append(hex.toHexDigits((byte)regs.get(Reg.AF)));
        sb.append("\nBC: $").append(hex.toHexDigits((byte)regs.get(Reg.BC)));
        sb.append("\nDE: $").append(hex.toHexDigits((byte)regs.get(Reg.DE)));
        sb.append("\nHL: $").append(hex.toHexDigits((byte)regs.get(Reg.HL)));
        sb.append("\nSP: $").append(hex.toHexDigits((short)regs.get(Reg.SP)));
        sb.append("\nPC: $").append(hex.toHexDigits((short)regs.get(Reg.PC)));

        CuteLogger.log(Level.INFO, sb.toString());
    }

    public void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new GridLayout(1,1)); // for later?
        frame.add(displayComponent);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        makePopupMenu();

        JComponent pane = frame.getRootPane();
        final InputMap in = pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        in.put(KeyStroke.getKeyStroke("A"), "left_down");
        in.put(KeyStroke.getKeyStroke("released A"), "left_up");
        in.put(KeyStroke.getKeyStroke("D"), "right_down");
        in.put(KeyStroke.getKeyStroke("released D"), "right_up");
        in.put(KeyStroke.getKeyStroke("W"), "up_down");
        in.put(KeyStroke.getKeyStroke("released W"), "up_up");
        in.put(KeyStroke.getKeyStroke("S"), "down_down");
        in.put(KeyStroke.getKeyStroke("released S"), "down_up");
        in.put(KeyStroke.getKeyStroke("O"), "a_button_down");
        in.put(KeyStroke.getKeyStroke("released O"), "a_button_up");
        in.put(KeyStroke.getKeyStroke("K"), "b_button_down");
        in.put(KeyStroke.getKeyStroke("released K"), "b_button_up");
        in.put(KeyStroke.getKeyStroke("T"), "start_button_down");
        in.put(KeyStroke.getKeyStroke("released T"), "start_button_up");
        in.put(KeyStroke.getKeyStroke("Y"), "select_button_down");
        in.put(KeyStroke.getKeyStroke("released Y"), "select_button_up");

        in.put(KeyStroke.getKeyStroke("F1"), "toggle_show_debugger");
        in.put(KeyStroke.getKeyStroke("F2"), "toggle_debugging");
        in.put(KeyStroke.getKeyStroke("F3"), "step_forward");
        in.put(KeyStroke.getKeyStroke("F4"), "load_rom");
        in.put(KeyStroke.getKeyStroke("F5"), "save_state");
        in.put(KeyStroke.getKeyStroke("F6"), "load_state");

        in.put(KeyStroke.getKeyStroke("F9"), "add_breakpoint");
        in.put(KeyStroke.getKeyStroke("F10"), "remove_breakpoint");

        in.put(KeyStroke.getKeyStroke("F12"), "reset");


        final ActionMap act = pane.getActionMap();
        act.put("left_down", new PressAction(GameButton.LEFT));
        act.put("right_down", new PressAction(GameButton.RIGHT));
        act.put("up_down", new PressAction(GameButton.UP));
        act.put("down_down", new PressAction(GameButton.DOWN));
        act.put("a_button_down", new PressAction(GameButton.A));
        act.put("b_button_down", new PressAction(GameButton.B));
        act.put("start_button_down", new PressAction(GameButton.START));
        act.put("select_button_down", new PressAction(GameButton.SELECT));

        act.put("left_up", new ReleaseAction(GameButton.LEFT));
        act.put("right_up", new ReleaseAction(GameButton.RIGHT));
        act.put("up_up", new ReleaseAction(GameButton.UP));
        act.put("down_up", new ReleaseAction(GameButton.DOWN));
        act.put("a_button_up", new ReleaseAction(GameButton.A));
        act.put("b_button_up", new ReleaseAction(GameButton.B));
        act.put("start_button_up", new ReleaseAction(GameButton.START));
        act.put("select_button_up", new ReleaseAction(GameButton.SELECT));

        act.put("toggle_show_debugger", new ToggleDebuggerVisibilityAction());
        act.put("toggle_debugging", new ToggleDebuggingAction());
        act.put("step_forward", new StepForwardAction());
        act.put("load_rom", new LoadROMAction());
        act.put("save_state", new SaveStateAction());
        act.put("load_state", new LoadStateAction());
        act.put("add_breakpoint", new AddBreakpointAction());
        act.put("remove_breakpoint", new RemoveBreakpointAction());
        act.put("reset", new ResetAction());
    }

    /**
     * Interface implementation
     */
    public void debuggerToggled() {
        if (debugViewer.getEmulatorPaused()) {
            frame.setTitle("gbEmu (paused)");
            debugViewer.postStepUpdate();
        } else {
            frame.setTitle("gbEmu");
        }
    }

    /**
     * Builds the right-click menu for the main emulator window
     */
    private void makePopupMenu() {
        frame.addMouseListener(new MouseListener() {

            @Override public void mousePressed(final MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON3) return;

                popupMenu.show(e.getComponent(), e.getX(), e.getY());

            }

            // rest are of no importance
            @Override public void mouseClicked(final MouseEvent e) {}
            @Override public void mouseReleased(final MouseEvent e) {}
            @Override public void mouseEntered(final MouseEvent e) {}
            @Override public void mouseExited(final MouseEvent e) {}
        });

        JMenuItem openROM = new JMenuItem("Load ROM") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F4");
            }
        };
        openROM.addActionListener(new LoadROMAction());
        popupMenu.add(openROM);

        JMenuItem saveState = new JMenuItem("Save State") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F5");
            }
        };
        saveState.addActionListener(new SaveStateAction());
        popupMenu.add(saveState);

        JMenuItem loadState = new JMenuItem("Load State") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F6");
            }
        };
        loadState.addActionListener(new LoadStateAction());
        popupMenu.add(loadState);

        JMenuItem reset = new JMenuItem("Reset game") {
            @Override public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F12");
            }
        };
        reset.addActionListener(new ResetAction());
        popupMenu.add(reset);

        popupMenu.addSeparator();

        JMenuItem toggleShowDebugger = new JMenuItem("Toggle debugging window") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F1");
            }
        };
        toggleShowDebugger.addActionListener(new ToggleDebuggerVisibilityAction());
        popupMenu.add(toggleShowDebugger);

        JMenuItem togglePause = new JMenuItem("Toggle pause") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F2");
            }
        };
        togglePause.addActionListener(new ToggleDebuggingAction());
        popupMenu.add(togglePause);

        JMenuItem step = new JMenuItem("Step forward") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("F3");
            }
        };
        step.addActionListener(new StepForwardAction());
        popupMenu.add(step);

        popupMenu.addSeparator();

        JMenuItem exit = new JMenuItem("Exit") {
            @Override
            public KeyStroke getAccelerator() {
                return KeyStroke.getKeyStroke("alt F4");
            }
        };
        exit.addActionListener(new ExitAction());
        popupMenu.add(exit);
    }

    /**
     * Runs when the main window is resized; recalculates a new scale factor (uses integer scaling) for the game graphics and places the
     * screen in the center of the window.
     * @param e the event to be processed
     */
    @Override public void componentResized(final ComponentEvent e) {
        Dimension size = frame.getContentPane().getSize();
        int scaleHeight = size.height / 144;
        int scaleWidth = size.width / 160;
        if (scaleHeight <= scaleWidth) {
            displayComponent.setScalingFactor(scaleHeight);
        } else {
            displayComponent.setScalingFactor(scaleWidth);
        }

        displayComponent.setLocation((size.width - displayComponent.getPreferredSize().width) / 2, (size.height - displayComponent.getPreferredSize().height) / 2);
    }

    @Override public void componentMoved(final ComponentEvent e) {}

    @Override public void componentShown(final ComponentEvent e) {}

    @Override public void componentHidden(final ComponentEvent e) {}

    @Override public void windowClosing(final WindowEvent e) {
        saveRAM();
    }

    private class LoadROMAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            changeROM();
	}
    }

    private class SaveStateAction extends AbstractAction {

        @Override public void actionPerformed(final ActionEvent e) {
            saveState();
        }
    }

    private class LoadStateAction extends AbstractAction {
        @Override public void actionPerformed(final ActionEvent e) {
            loadState();
        }
    }

    private class PressAction extends AbstractAction {
        private final GameButton button;

        private PressAction(GameButton button) {
            this.button = button;
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            memory.setButton(this.button);
        }
    }

    private class ReleaseAction extends AbstractAction {
        private final GameButton button;

        private ReleaseAction(GameButton button) {
            this.button = button;
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            memory.releaseButton(this.button);
        }
    }

    private class ToggleDebuggerVisibilityAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            debugViewer.toggleVisibility();
            if (debugViewer.getEmulatorPaused()) {
                frame.setTitle("gbEmu (paused)");
                debugViewer.postStepUpdate();
            } else {
                frame.setTitle("gbEmu");
            }
        }
    }

    private class ToggleDebuggingAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            debugViewer.toggleDebugging();
            debuggerToggled();
        }
    }

    private class StepForwardAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            if (!debugViewer.getEmulatorPaused()) new ToggleDebuggingAction().actionPerformed(e);
            debugViewer.step();
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
            removeBreakpoint();
        }
    }

    private class ResetAction extends AbstractAction {
        @Override public void actionPerformed(final ActionEvent e) {
            resetROM();
        }
    }

    private class ExitAction extends AbstractAction
    {
        @Override public void actionPerformed(final ActionEvent e) {
            saveRAM();
            System.exit(0);
        }
    }
}
