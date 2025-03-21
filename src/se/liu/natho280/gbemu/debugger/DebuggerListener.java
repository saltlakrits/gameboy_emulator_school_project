package se.liu.natho280.gbemu.debugger;

/**
 * The main UI is a listener to the Debugger, and when certain actions are run in the Debugger (mostly regarding keyboard shortcuts &
 * menu choices) the main UI needs to handle them.
 */
public interface DebuggerListener {
    public void debuggerToggled();
    public void changeROM();
    public void loadState();
    public void saveState();
    public void resetROM();
}