# cvmfs-java

[![Java CI](https://github.com/moliholy/cvmfs-java/actions/workflows/java.yml/badge.svg)](https://github.com/moliholy/cvmfs-java/actions/workflows/java.yml)
[![codecov](https://codecov.io/gh/moliholy/cvmfs-java/graph/badge.svg)](https://codecov.io/gh/moliholy/cvmfs-java)
[![License](https://img.shields.io/badge/license-BSD%203--Clause-blue.svg)](LICENSE)

A Java library for interacting with [CernVM-FS](https://cernvm.cern.ch/fs) repositories, a scalable, reliable, and low-maintenance software distribution service used in high-energy physics and scientific computing.

## Features

- **Repository access**: read and navigate CVMFS repositories over HTTP or local paths
- **Manifest parsing**: parse and validate `.cvmfspublished` files with all standard fields
- **Catalog traversal**: SQLite-backed catalog queries with nested catalog support
- **Directory listing**: list files and directories at any path in the repository
- **File retrieval**: download and decompress files from content-addressable storage
- **Chunked files**: support for chunked file storage with hash verification
- **History database**: access tagged revisions and repository history
- **Signature verification**: X.509 certificate parsing and RSA signature verification
- **Whitelist validation**: repository name matching and expiry checks
- **Content hashing**: SHA-1, RIPEMD-160, SHA-256, and SHAKE-128 support
- **Local caching**: two-level hex-prefix cache directory structure
- **Modern Java**: built on Java 21 with records, sealed types, pattern matching, and `java.time`

## Prerequisites

- Java 21+
- Maven 3.9+

## Quick start

```sh
git clone https://github.com/moliholy/cvmfs-java.git
cd cvmfs-java
mvn clean install
```

## Library usage

```java
import com.molina.cvmfs.fetcher.Fetcher;
import com.molina.cvmfs.repository.Repository;

// Initialize
var fetcher = new Fetcher("http://cvmfs-stratum-one.cern.ch/opt/boss", "/tmp/cvmfs-cache", true);
var repo = new Repository(fetcher);

System.out.println("Repository: " + repo.fqrn());
System.out.println("Revision: " + repo.getRevisionNumber());

// List directory
for (var entry : repo.listDirectory("/")) {
    System.out.println(entry.name() + (entry.isDirectory() ? "/" : ""));
}

// Look up a file
var entry = repo.lookup("/testfile");
System.out.println("Size: " + entry.size());

// Read a file
var path = repo.getFile("/testfile");
var content = java.nio.file.Files.readString(path);
```

### History and revisions

```java
// Access history
var history = repo.retrieveHistory();
System.out.println("Schema: " + history.schema());

// Get tag by name
var tag = history.getTagByName("trunk");
tag.ifPresent(t -> System.out.println("Trunk revision: " + t.revision()));

// Browse a specific revision
repo.setCurrentTag(293);
var entries = repo.listDirectory("/");
```

### Catalog operations

```java
// Retrieve root catalog
var catalog = repo.retrieveCurrentRootCatalog();
System.out.println("Root: " + catalog.isRoot());
System.out.println("Nested catalogs: " + catalog.nestedCount());

// Statistics
var stats = catalog.getStatistics();
System.out.println("Files: " + stats.regular());
System.out.println("Dirs: " + stats.dir());
```

## Development

```sh
make build              # Compile
make test               # Run all tests
make test-unit          # Unit tests only
make test-integration   # Integration tests only
make coverage           # Generate JaCoCo coverage report
make check              # Build + all tests
make clean              # Clean build artifacts
make doc                # Generate Javadoc
```

## Project structure

```
src/main/java/com/molina/cvmfs/
├── catalog/          Catalog database wrapper, nested catalogs, statistics
├── certificate/      X.509 certificate parsing and verification
├── common/           Shared utilities, exceptions, database abstraction
├── directoryentry/   File/directory metadata, chunks, content hash types
├── fetcher/          HTTP/file retrieval, local cache management
├── history/          Repository history database, revision tags
├── manifest/         .cvmfspublished parsing and validation
├── repository/       Main entry point for repository operations
├── revision/         Revision snapshots and iteration
├── rootfile/         Base class for signed root files
└── whitelist/        .cvmfswhitelist parsing and validation
```

## Related projects

- [cvmfs-rust](https://github.com/moliholy/cvmfs-rust): Rust implementation with FUSE filesystem support
- [CernVM-FS](https://github.com/cvmfs/cvmfs): the original C++ implementation

## License

This project is licensed under the [BSD 3-Clause License](LICENSE).
