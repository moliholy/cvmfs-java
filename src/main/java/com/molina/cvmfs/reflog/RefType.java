package com.molina.cvmfs.reflog;

public enum RefType {
    CATALOG(0),
    CERTIFICATE(1),
    HISTORY(2),
    META_INFO(3);

    private final int value;

    RefType(int value) { this.value = value; }

    public int value() { return value; }

    public static RefType fromValue(int value) {
        return switch (value) {
            case 0 -> CATALOG;
            case 1 -> CERTIFICATE;
            case 2 -> HISTORY;
            case 3 -> META_INFO;
            default -> throw new IllegalArgumentException("Unknown ref type: " + value);
        };
    }
}
