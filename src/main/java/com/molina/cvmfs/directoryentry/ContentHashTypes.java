package com.molina.cvmfs.directoryentry;

public enum ContentHashTypes {
    UNKNOWN(""),
    SHA1(""),
    RIPEMD160("-rmd160"),
    SHA256("-sha256"),
    SHAKE128("-shake128");

    private final String suffix;

    ContentHashTypes(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    public static ContentHashTypes fromValue(int value) {
        return switch (value) {
            case 1 -> SHA1;
            case 2 -> RIPEMD160;
            case 3 -> SHA256;
            case 4 -> SHAKE128;
            default -> UNKNOWN;
        };
    }
}
