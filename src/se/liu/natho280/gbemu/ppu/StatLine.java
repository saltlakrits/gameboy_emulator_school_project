package se.liu.natho280.gbemu.ppu;

/**
 * This probably looks a little nonsensical. Easiest to follow the link and read about it! This is a simple
 * implementation of the "signal" they mention. Should return true on a rising edge.
 * @see <a href="https://gbdev.io/pandocs/Interrupt_Sources.html#int-48--stat-interrupt">Pan Docs - STAT Interrupt</a>
 */
public class StatLine {
    private int line = 0;

    /**
     * Add a low (false) or high (true) signal to the status-line. With a rising edge in the signal, we should
     * request an interrupt.
     * @param isHigh signal to add
     * @return
     */
    public boolean addSignal(boolean isHigh) {
        // imagine the line as a digital signal going from MSB to LSB, with 1 signifying high and 0 low
        // we should return true (set STAT interrupt) if signal goes from low to high

        line <<= 1; // shift left
        line &= 0xF; // only keep rightmost 4 bits
        line |= isHigh ? 1 : 0; // OR in the new signal
        return (line & 0x3) == 0x1; // return whether line went from low to high -> STAT interrupt
    }

    public void reset() {
        line = 0;
    }
}
