package com.molina.cvmfs.common;

import java.io.IOException;

public interface FileLike {
    int readAt(long offset, byte[] buf) throws IOException;
    long fileSize();
}
