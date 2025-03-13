package se.liu.natho280.gbemu.ppu;

/**
 * In the se.liu.natho280.GbEmu.OAM region of the memory we put 4 bytes, an se.liu.natho280.GbEmu.OAM -- an Object Attribute Map. This class exists simply for
 * ergonomics in handling them.
 * @see <a href=https://gbdev.io/pandocs/OAM.html>Pan Docs - Object Attribute se.liu.natho280.GbEmu.Memory (se.liu.natho280.GbEmu.OAM)</a>
 */
public class OAM {
    private final int y;
    private final int x;
    private final int index; // tile index
    private final int flags;

    public OAM(int y, int x, int index, int flags) {
        this.y = y;
        this.x = x;
        this.index = index; // to be multiplied by 0x10 and added to 0x8000 to access an actual tile
        this.flags = flags; // bit flags, maybe argument makes no sense
    }

    public int getTileAddress() {
        return 0x8000 + (0x10 * index);
    }

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }

    public boolean getFlagPriority() {
        return (flags & (1 << 7)) != 0;
    }

    public boolean getFlagYFlip() {
        return (flags & (1 << 6)) != 0;
    }

    public boolean getFlagXFlip() {
        return (flags & (1 << 5)) != 0;
    }

    public boolean getFlagPalette() {
        return (flags & (1 << 4)) != 0;
    }
}
