package se.liu.natho280.gbemu.debugger;

/**
 * Certain debug features requires listening on memory writes, this enables them
 * to do that.
 */
public interface MemoryListener {
    public void memoryChanged(int index);
}
