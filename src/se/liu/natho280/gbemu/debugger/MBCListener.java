package se.liu.natho280.gbemu.debugger;

/**
 * Certain debug features requires listening on MBC bank switches, this enables them
 * to do that.
 */
public interface MBCListener {
    public void bankSwitched();
}
