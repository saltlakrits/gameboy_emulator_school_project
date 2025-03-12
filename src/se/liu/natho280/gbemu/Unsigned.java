package se.liu.natho280.gbemu;

/**
 * Java has no unsigned integers, they are all signed by default. This is a small, naive abstract class for declaring
 * unsigned integers. They will all be likely 32-bit ints (if not 64) behind the scenes, but will clamp their values
 * according to the constraints.
 */
public abstract class Unsigned {
    private int value = 0;

    protected Unsigned(final int value) {
        set(value);
    }

    public void set(int value) {
        this.value = clamp(value);
    }

    public int get() {
        return this.value;
    }

    public void add(int n) {
        value = clamp(this.value + n);
    }

    public void sub(int n) {
        this.value = clamp(this.value - n);
    }

    abstract protected int clamp(int value);
}
