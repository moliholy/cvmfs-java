package com.molina.cvmfs.filesystem;

import com.molina.cvmfs.common.ChunkedFile;
import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.common.FileLike;
import com.molina.cvmfs.common.RegularFile;
import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.repository.Repository;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CvmfsFileSystem extends FuseStubFS {
    private final Repository repository;
    private final Map<String, DirectoryEntry> lookupCache = new ConcurrentHashMap<>();
    private final Map<Long, OpenFile> openFiles = new ConcurrentHashMap<>();
    private final AtomicLong nextFh = new AtomicLong(1);

    record OpenFile(String path, FileLike file) {}

    public CvmfsFileSystem(Repository repository) {
        this.repository = repository;
    }

    private DirectoryEntry cachedLookup(String path) throws CvmfsException {
        var cached = lookupCache.get(path);
        if (cached != null) return cached;
        var entry = repository.lookup(path);
        lookupCache.put(path, entry);
        return entry;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        try {
            var entry = cachedLookup(path);
            fillStat(entry, stat);
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.ENOENT();
        }
    }

    @Override
    public int readlink(String path, Pointer buf, @size_t long size) {
        try {
            var entry = cachedLookup(path);
            if (!entry.isSymlink()) return -ErrorCodes.EINVAL();
            var target = entry.symlink();
            if (target == null) return -ErrorCodes.ENOENT();
            var bytes = target.getBytes();
            int len = (int) Math.min(bytes.length, size - 1);
            buf.put(0, bytes, 0, len);
            buf.putByte(len, (byte) 0);
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.ENOENT();
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        try {
            var entry = cachedLookup(path);
            if (!entry.isFile()) return -ErrorCodes.EISDIR();
            var file = openFileLike(path, entry);
            long fh = nextFh.getAndIncrement();
            openFiles.put(fh, new OpenFile(path, file));
            fi.fh.set(fh);
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.ENOENT();
        }
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        var of = openFiles.get(fi.fh.get());
        if (of == null) return -ErrorCodes.EBADF();
        try {
            var data = new byte[(int) Math.min(size, of.file().fileSize() - offset)];
            if (data.length == 0) return 0;
            int read = of.file().readAt(offset, data);
            if (read <= 0) return 0;
            buf.put(0, data, 0, read);
            return read;
        } catch (IOException e) {
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        var of = openFiles.remove(fi.fh.get());
        if (of != null && of.file() instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Exception ignored) {}
        }
        return 0;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        try {
            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);
            var entries = repository.listDirectory(path);
            for (var entry : entries) {
                var stat = new FileStat(jnr.ffi.Runtime.getSystemRuntime());
                fillStat(entry, stat);
                var childPath = "/".equals(path) ? "/" + entry.name() : path + "/" + entry.name();
                lookupCache.put(childPath, entry);
                filter.apply(buf, entry.name(), stat, 0);
            }
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.ENOENT();
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        try {
            var stats = repository.getStatistics();
            stbuf.f_bsize.set(512);
            stbuf.f_frsize.set(512);
            stbuf.f_blocks.set(1 + stats.fileSize() / 512);
            stbuf.f_bfree.set(0);
            stbuf.f_bavail.set(0);
            stbuf.f_files.set(stats.regular());
            stbuf.f_ffree.set(0);
            stbuf.f_namemax.set(255);
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int access(String path, int mask) {
        try {
            cachedLookup(path);
            return 0;
        } catch (CvmfsException e) {
            return -ErrorCodes.ENOENT();
        }
    }

    private FileLike openFileLike(String path, DirectoryEntry entry) throws CvmfsException {
        try {
            if (entry.hasChunks()) {
                return new ChunkedFile(entry.chunks(), entry.size(), repository.fetcher());
            }
            var hash = entry.contentHashString()
                    .orElseThrow(() -> new CvmfsException("No content hash for: " + path));
            var filePath = repository.retrieveObject(hash);
            return new RegularFile(filePath);
        } catch (IOException e) {
            throw new CvmfsException("Failed to open: " + path, e);
        }
    }

    private void fillStat(DirectoryEntry entry, FileStat stat) {
        if (entry.isDirectory()) {
            stat.st_mode.set(FileStat.S_IFDIR | (entry.mode() & 0x1FF));
        } else if (entry.isSymlink()) {
            stat.st_mode.set(FileStat.S_IFLNK | 0777);
        } else {
            stat.st_mode.set(FileStat.S_IFREG | (entry.mode() & 0x1FF));
        }
        stat.st_size.set(entry.size());
        stat.st_nlink.set((short) entry.nlink());
        stat.st_uid.set(entry.uid());
        stat.st_gid.set(entry.gid());
        var mtime = entry.mtime();
        stat.st_mtim.tv_sec.set(mtime);
        stat.st_atim.tv_sec.set(mtime);
        stat.st_ctim.tv_sec.set(mtime);
        stat.st_blocks.set(1 + entry.size() / 512);
    }
}
