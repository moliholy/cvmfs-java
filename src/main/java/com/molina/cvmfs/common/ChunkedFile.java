package com.molina.cvmfs.common;

import com.molina.cvmfs.directoryentry.Chunk;
import com.molina.cvmfs.fetcher.Fetcher;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkedFile implements FileLike, AutoCloseable {
    static final int PREFETCH_AHEAD = 8;

    private final long size;
    private final List<Chunk> chunks;
    private final Fetcher fetcher;
    private final ExecutorService prefetchExecutor;

    private int cachedIndex = -1;
    private RandomAccessFile cachedFile;
    private final Set<Integer> prefetched = new HashSet<>();

    public ChunkedFile(List<Chunk> chunks, long size, Fetcher fetcher) {
        this.chunks = List.copyOf(chunks);
        this.size = size;
        this.fetcher = fetcher;
        this.prefetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    int findChunkIndex(long position) {
        if (position < 0 || position >= size) return -1;
        int lo = 0, hi = chunks.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            var chunk = chunks.get(mid);
            if (position < chunk.offset()) {
                hi = mid;
            } else if (position >= chunk.offset() + chunk.size()) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private RandomAccessFile openChunk(int index) throws IOException {
        var chunk = chunks.get(index);
        var path = Common.composeObjectPath(chunk.contentHash(), chunk.contentHashType().suffix());
        try {
            Path file = fetcher.retrieveFile(path);
            return new RandomAccessFile(file.toFile(), "r");
        } catch (CvmfsException e) {
            throw new IOException("Failed to retrieve chunk: " + chunk.contentHash(), e);
        }
    }

    private void prefetchChunks(int fromIndex) {
        for (int i = fromIndex + 1; i < Math.min(fromIndex + 1 + PREFETCH_AHEAD, chunks.size()); i++) {
            if (prefetched.contains(i)) continue;
            prefetched.add(i);
            int idx = i;
            prefetchExecutor.submit(() -> {
                try {
                    var chunk = chunks.get(idx);
                    var path = Common.composeObjectPath(chunk.contentHash(), chunk.contentHashType().suffix());
                    fetcher.retrieveFile(path);
                } catch (CvmfsException ignored) {}
            });
        }
    }

    @Override
    public synchronized int readAt(long offset, byte[] buf) throws IOException {
        if (buf.length == 0 || offset >= size) return 0;

        int totalRead = 0;
        long position = offset;

        while (totalRead < buf.length && position < size) {
            int chunkIdx = findChunkIndex(position);
            if (chunkIdx < 0) break;

            if (chunkIdx != cachedIndex) {
                if (cachedFile != null) cachedFile.close();
                cachedFile = openChunk(chunkIdx);
                cachedIndex = chunkIdx;
                prefetchChunks(chunkIdx);
            }

            var chunk = chunks.get(chunkIdx);
            long chunkOffset = position - chunk.offset();
            int bytesAvailable = (int) Math.min(chunk.size() - chunkOffset, buf.length - totalRead);

            cachedFile.seek(chunkOffset);
            int read = cachedFile.read(buf, totalRead, bytesAvailable);
            if (read <= 0) break;

            totalRead += read;
            position += read;
        }
        return totalRead > 0 ? totalRead : -1;
    }

    @Override
    public long fileSize() {
        return size;
    }

    @Override
    public void close() throws IOException {
        prefetchExecutor.shutdownNow();
        if (cachedFile != null) cachedFile.close();
    }

    public List<Chunk> chunks() { return chunks; }
}
