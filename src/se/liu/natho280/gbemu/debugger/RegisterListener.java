package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Reg;

/**
 * Certain debug features requires listening on register value changes, this enables them
 * to do that.
 */
public interface RegisterListener {
    public void registerUpdated(Reg reg);
}
