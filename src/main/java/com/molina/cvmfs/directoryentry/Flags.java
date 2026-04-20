package com.molina.cvmfs.directoryentry;

public final class Flags {
    public static final int DIRECTORY = 1;
    public static final int NESTED_CATALOG_MOUNTPOINT = 2;
    public static final int FILE = 4;
    public static final int LINK = 8;
    public static final int FILE_STAT = 16;
    public static final int NESTED_CATALOG_ROOT = 32;
    public static final int FILE_CHUNK = 64;
    public static final int EXTERNAL_FILE = 128;
    public static final int CONTENT_HASH_TYPE = 0x700;

    private Flags() {}
}
