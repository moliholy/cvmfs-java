package com.molina.cvmfs.common;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegularFile implements FileLike, AutoCloseable {
    private final RandomAccessFile raf;
    private final long size;

    public RegularFile(Path path) throws IOException {
        this.size = Files.size(path);
        this.raf = new RandomAccessFile(path.toFile(), "r");
    }

    @Override
    public synchronized int readAt(long offset, byte[] buf) throws IOException {
        raf.seek(offset);
        return raf.read(buf);
    }

    @Override
    public long fileSize() {
        return size;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
