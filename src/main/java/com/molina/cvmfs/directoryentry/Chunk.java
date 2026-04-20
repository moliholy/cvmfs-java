package com.molina.cvmfs.directoryentry;

import com.molina.cvmfs.common.Common;

public class Chunk {
    private final int offset;
    private final int size;
    private final String contentHash;
    private final ContentHashTypes contentHashType;

    public Chunk(int offset, int size, byte[] hashBytes, ContentHashTypes contentHashType) {
        this.offset = offset;
        this.size = size;
        this.contentHash = Common.toHex(hashBytes);
        this.contentHashType = contentHashType;
    }

    public static String catalogDatabaseFields() {
        return "md5path_1, md5path_2, offset, size, hash";
    }

    public String contentHashString() {
        return contentHash + contentHashType.suffix();
    }

    public int offset() { return offset; }
    public int size() { return size; }
    public String contentHash() { return contentHash; }
    public ContentHashTypes contentHashType() { return contentHashType; }
}
