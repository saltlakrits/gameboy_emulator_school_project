package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Reg;

public interface RegisterListener {
    public void registerUpdated(Reg reg);
}
