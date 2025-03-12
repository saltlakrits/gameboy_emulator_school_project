import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The frontend window that displays the emulator screen and receives the input from the user.
 */
public class EmuViewer {
    // Frontend for the emulator

    private Memory memory = null;
    private Display display = null;
    private final JFrame frame = new JFrame("gbEmu");
    private final MemoryViewer memoryViewer;

    public EmuViewer(Memory memory, Display display, MemoryViewer memoryViewer) {
        this.memory = memory;
        this.display = display;
        this.memoryViewer = memoryViewer;
    }

    public void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final DisplayComponent displayComp = new DisplayComponent(display);
        frame.setLayout(new GridLayout(1,1)); // for later!
        frame.add(displayComp);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

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
        act.put("left_down", new PressAction(Button.LEFT));
        act.put("right_down", new PressAction(Button.RIGHT));
        act.put("up_down", new PressAction(Button.UP));
        act.put("down_down", new PressAction(Button.DOWN));
        act.put("a_button_down", new PressAction(Button.A));
        act.put("b_button_down", new PressAction(Button.B));
        act.put("start_button_down", new PressAction(Button.START));
        act.put("select_button_down", new PressAction(Button.SELECT));

        act.put("toggle_show_debugger", new ToggleDebuggerVisibilityAction());
        act.put("toggle_debugging", new ToggleDebuggingAction());
        act.put("step_forward", new StepForwardAction());

        act.put("left_up", new ReleaseAction(Button.LEFT));
        act.put("right_up", new ReleaseAction(Button.RIGHT));
        act.put("up_up", new ReleaseAction(Button.UP));
        act.put("down_up", new ReleaseAction(Button.DOWN));
        act.put("a_button_up", new ReleaseAction(Button.A));
        act.put("b_button_up", new ReleaseAction(Button.B));
        act.put("start_button_up", new ReleaseAction(Button.START));
        act.put("select_button_up", new ReleaseAction(Button.SELECT));
    }

    private class PressAction extends AbstractAction {
        private final Button button;

        public PressAction(Button button) {
            this.button = button;
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            memory.setButton(this.button);
        }
    }

    private class ReleaseAction extends AbstractAction {
        private final Button button;

        public ReleaseAction(Button button) {
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
