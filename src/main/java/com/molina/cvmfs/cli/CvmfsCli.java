package com.molina.cvmfs.cli;

import com.molina.cvmfs.fetcher.Fetcher;
import com.molina.cvmfs.filesystem.CvmfsFileSystem;
import com.molina.cvmfs.repository.Repository;

import java.nio.file.Files;
import java.nio.file.Path;

public class CvmfsCli {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: cvmfs-java <repository_url> <mount_point> [cache_directory]");
            System.exit(1);
        }

        var repoUrl = args[0];
        var mountPoint = Path.of(args[1]);
        var cacheDir = args.length > 2 ? args[2] : "/tmp/cvmfs_java";

        if (!Files.isDirectory(mountPoint)) {
            System.err.println("Mount point does not exist or is not a directory: " + mountPoint);
            System.exit(1);
        }

        try {
            Files.createDirectories(Path.of(cacheDir));
            var fetcher = new Fetcher(repoUrl, cacheDir, true);
            var repository = new Repository(fetcher);
            var fs = new CvmfsFileSystem(repository);

            System.out.println("Mounting " + repository.fqrn() + " at " + mountPoint);
            fs.mount(mountPoint, false, false, new String[]{"-o", "fsname=cvmfs-java"});
        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            System.exit(1);
        }
    }
}
