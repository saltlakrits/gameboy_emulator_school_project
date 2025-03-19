package se.liu.natho280.gbemu.debugger;

public interface DebuggerListener {
    public void debuggerToggled();
    public void changeROM();
    public void loadState();
    public void saveState();
    public void resetROM();
}
