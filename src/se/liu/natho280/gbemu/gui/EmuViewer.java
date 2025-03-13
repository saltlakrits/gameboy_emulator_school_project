package se.liu.natho280.gbemu.gui;

import se.liu.natho280.gbemu.cpu.CPU;
import se.liu.natho280.gbemu.cpu.GameButton;
import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.debugger.MemoryViewer;
import se.liu.natho280.gbemu.ppu.Display;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * The frontend window that displays the emulator screen and receives the input from the user.
 */
public class EmuViewer {
    // Frontend for the emulator

    private CPU cpu;
    private Memory memory;
    private Display display;
    private final JFrame frame = new JFrame("gbEmu");
    private final JPopupMenu popupMenu = new JPopupMenu();
    private final MemoryViewer memoryViewer;

    public EmuViewer(CPU cpu, Memory memory, Display display, MemoryViewer memoryViewer) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        this.memoryViewer = memoryViewer;
    }

    /**
     * When we change the ROM through the UI, we need to reinitialize
     * the CPU and Memory. The PPU shouldn't care that we do so.
     * @param newROM path to new ROM file
     */
    private void changeROM(String newROM) {
        memoryViewer.setEmulatorPaused(true); // pause emulator while reinitializing it
        this.memory.reInitializeMemory(newROM); // reinitialize memory (zero it out), inside it reInit ROM as well?
        this.cpu.reInitializeCPU(); // reinit registers? anything else? run setUpBoot() again
        this.memoryViewer.redisassemble();
        memoryViewer.setEmulatorPaused(false); // done reinitializing -> unpause
    }

    public void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final DisplayComponent displayComp = new DisplayComponent(display);
        frame.setLayout(new GridLayout(1,1)); // for later!
        frame.add(displayComp);
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
        in.put(KeyStroke.getKeyStroke("F4"), "toggle_debugging");
        in.put(KeyStroke.getKeyStroke("F5"), "step_forward");

        final ActionMap act = pane.getActionMap();
        act.put("left_down", new PressAction(GameButton.LEFT));
        act.put("right_down", new PressAction(GameButton.RIGHT));
        act.put("up_down", new PressAction(GameButton.UP));
        act.put("down_down", new PressAction(GameButton.DOWN));
        act.put("a_button_down", new PressAction(GameButton.A));
        act.put("b_button_down", new PressAction(GameButton.B));
        act.put("start_button_down", new PressAction(GameButton.START));
        act.put("select_button_down", new PressAction(GameButton.SELECT));

        act.put("toggle_show_debugger", new ToggleDebuggerVisibilityAction());
        act.put("toggle_debugging", new ToggleDebuggingAction());
        act.put("step_forward", new StepForwardAction());

        act.put("left_up", new ReleaseAction(GameButton.LEFT));
        act.put("right_up", new ReleaseAction(GameButton.RIGHT));
        act.put("up_up", new ReleaseAction(GameButton.UP));
        act.put("down_up", new ReleaseAction(GameButton.DOWN));
        act.put("a_button_up", new ReleaseAction(GameButton.A));
        act.put("b_button_up", new ReleaseAction(GameButton.B));
        act.put("start_button_up", new ReleaseAction(GameButton.START));
        act.put("select_button_up", new ReleaseAction(GameButton.SELECT));
    }

    private void makePopupMenu() {
        JMenuItem openROM = new JMenuItem("Load ROM");

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

        openROM.addActionListener(new LoadROMAction());
        popupMenu.add(openROM);
    }

    private class LoadROMAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
	    FileDialog fd = new FileDialog(frame, "Load ROM", FileDialog.LOAD);
            fd.setVisible(true);
            String newROMPath = fd.getDirectory() + fd.getFile();

            changeROM(newROMPath);
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
            memoryViewer.toggleVisibility();
            if (memoryViewer.getEmulatorPaused()) {
                frame.setTitle("gbEmu (paused)");
                memoryViewer.postStepUpdate();
            } else {
                frame.setTitle("gbEmu");
            }
        }
    }

    private class ToggleDebuggingAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            memoryViewer.toggleDebugging();
            if (memoryViewer.getEmulatorPaused()) {
                frame.setTitle("gbEmu (paused)");
                memoryViewer.postStepUpdate();
            } else {
                frame.setTitle("gbEmu");
            }
        }
    }

    private class StepForwardAction extends AbstractAction {
        @Override
        public void actionPerformed(final ActionEvent e) {
            memoryViewer.step();
        }
    }
}
