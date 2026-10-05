package edu.dac;

public enum SecurityLevel {
    UNCLASSIFIED(0, "Несекретно"),
    CONFIDENTIAL(1, "Для служебного пользования"),
    SECRET(2, "Секретно"),
    TOP_SECRET(3, "Совершенно секретно");

    private final int rank;
    private final String displayName;

    SecurityLevel(int rank, String displayName) {
        this.rank = rank;
        this.displayName = displayName;
    }

    public int rank() {
        return rank;
    }

    public String displayName() {
        return displayName;
    }

    public boolean mayRead(SecurityLevel objectLevel) {
        return rank >= objectLevel.rank;
    }

    public boolean mayWrite(SecurityLevel objectLevel) {
        return rank <= objectLevel.rank;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
