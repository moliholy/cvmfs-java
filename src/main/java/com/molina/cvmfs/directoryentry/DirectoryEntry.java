package com.molina.cvmfs.directoryentry;

import com.molina.cvmfs.common.Common;
import com.molina.cvmfs.common.PathHash;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DirectoryEntry {
    private final long md5path1;
    private final long md5path2;
    private final long parent1;
    private final long parent2;
    private final String contentHash;
    private final int flags;
    private final long size;
    private final int mode;
    private final long mtime;
    private final String name;
    private final String symlink;
    private final int uid;
    private final int gid;
    private final long hardlinks;
    private final ContentHashTypes contentHashType;
    private final List<Chunk> chunks;

    public DirectoryEntry(ResultSet rs) throws SQLException {
        this.chunks = new ArrayList<>();
        this.md5path1 = rs.getLong("md5path_1");
        this.md5path2 = rs.getLong("md5path_2");
        this.parent1 = rs.getLong("parent_1");
        this.parent2 = rs.getLong("parent_2");
        byte[] hashBytes = rs.getBytes("hash");
        this.contentHash = hashBytes != null ? Common.toHex(hashBytes) : null;
        this.flags = rs.getInt("flags");
        this.size = rs.getLong("size");
        this.mode = rs.getInt("mode");
        this.mtime = rs.getLong("mtime");
        this.name = rs.getString("name");
        this.symlink = rs.getString("symlink");
        // uid/gid may not exist in older schemas
        int tmpUid = 0, tmpGid = 0;
        try { tmpUid = rs.getInt("uid"); } catch (SQLException ignored) {}
        try { tmpGid = rs.getInt("gid"); } catch (SQLException ignored) {}
        this.uid = tmpUid;
        this.gid = tmpGid;
        long tmpHardlinks = 1;
        try { tmpHardlinks = rs.getLong("hardlinks"); } catch (SQLException ignored) {}
        this.hardlinks = tmpHardlinks;
        this.contentHashType = readContentHashType(flags);
    }

    // Package-private constructor for testing
    DirectoryEntry(long md5path1, long md5path2, long parent1, long parent2,
                   String contentHash, int flags, long size, int mode, long mtime,
                   String name, String symlink, int uid, int gid, long hardlinks,
                   List<Chunk> chunks) {
        this.md5path1 = md5path1;
        this.md5path2 = md5path2;
        this.parent1 = parent1;
        this.parent2 = parent2;
        this.contentHash = contentHash;
        this.flags = flags;
        this.size = size;
        this.mode = mode;
        this.mtime = mtime;
        this.name = name;
        this.symlink = symlink;
        this.uid = uid;
        this.gid = gid;
        this.hardlinks = hardlinks;
        this.contentHashType = readContentHashType(flags);
        this.chunks = chunks != null ? new ArrayList<>(chunks) : new ArrayList<>();
    }

    public static String catalogDatabaseFields() {
        return "md5path_1, md5path_2, parent_1, parent_2, hash, flags, " +
                "size, mode, mtime, name, symlink";
    }

    static ContentHashTypes readContentHashType(int flags) {
        int hashBits = (flags & Flags.CONTENT_HASH_TYPE) >> 8;
        return ContentHashTypes.fromValue(hashBits + 1);
    }

    public boolean isDirectory() { return (flags & Flags.DIRECTORY) != 0; }
    public boolean isNestedCatalogMountpoint() { return (flags & Flags.NESTED_CATALOG_MOUNTPOINT) != 0; }
    public boolean isNestedCatalogRoot() { return (flags & Flags.NESTED_CATALOG_ROOT) != 0; }
    public boolean isFile() { return (flags & Flags.FILE) != 0; }
    public boolean isSymlink() { return (flags & Flags.LINK) != 0; }
    public boolean isExternalFile() { return (flags & Flags.EXTERNAL_FILE) != 0; }
    public boolean hasChunks() { return contentHash == null; }

    public PathHash pathHash() { return new PathHash(md5path1, md5path2); }
    public PathHash parentHash() { return new PathHash(parent1, parent2); }

    public Optional<String> contentHashString() {
        if (contentHash == null) return Optional.empty();
        return Optional.of(contentHash + contentHashType.suffix());
    }

    public long nlink() { return hardlinks & 0xFFFFFFFFL; }
    public long hardlinkGroup() { return hardlinks >> 32; }

    public void addChunks(ResultSet rs) throws SQLException {
        chunks.clear();
        while (rs.next()) {
            int offset = rs.getInt("offset");
            int chunkSize = rs.getInt("size");
            byte[] hash = rs.getBytes("hash");
            chunks.add(new Chunk(offset, chunkSize, hash != null ? hash : new byte[0], contentHashType));
        }
    }

    public long md5path1() { return md5path1; }
    public long md5path2() { return md5path2; }
    public long parent1() { return parent1; }
    public long parent2() { return parent2; }
    public String contentHash() { return contentHash; }
    public int flags() { return flags; }
    public long size() { return size; }
    public int mode() { return mode; }
    public long mtime() { return mtime; }
    public String name() { return name; }
    public String symlink() { return symlink; }
    public int uid() { return uid; }
    public int gid() { return gid; }
    public long hardlinks() { return hardlinks; }
    public ContentHashTypes contentHashType() { return contentHashType; }
    public List<Chunk> chunks() { return chunks; }
}
