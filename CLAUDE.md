# cvmfs-java

Java 21 library for reading CernVM-FS repositories.

## Build

```sh
mvn compile
mvn test                     # unit tests
mvn verify                   # unit + integration tests
mvn test jacoco:report       # coverage report
```

## Architecture

- `repository/Repository` - main entry point, coordinates catalogs, manifest, history
- `catalog/Catalog` - SQLite-backed metadata queries, nested catalog support
- `manifest/Manifest` - parses `.cvmfspublished` key-value format
- `fetcher/Fetcher` - HTTP downloads with zlib decompression, uses `java.net.http.HttpClient`
- `fetcher/Cache` - local 2-level hex-prefix cache (`data/XX/`)
- `directoryentry/DirectoryEntry` - file/dir metadata from catalog DB
- `history/History` - SQLite history DB with tagged revisions
- `whitelist/Whitelist` - parses `.cvmfswhitelist` with fingerprints and expiry
- `rootfile/RootFile` - abstract base for signed root files (manifest, whitelist)
- `common/CvmfsException` - single exception type for all CVMFS errors

## Key patterns

- Records: `PathHash`, `RevisionTag`, `CatalogReference`, `DirectoryEntryWrapper`
- `ContentHashTypes` is an enum (SHA1, RIPEMD160, SHA256, SHAKE128)
- `Optional` returned from lookups (no nulls at API boundary)
- `java.time.Instant` for timestamps (not `java.util.Date`)
- `java.nio.file.Path` for file paths (not `java.io.File`)
- `AutoCloseable` on `DatabaseObject`, `Catalog`, `History`
- SQL injection prevented via `PreparedStatement` everywhere

## Tests

- Unit tests: `src/test/java/` (no network)
- Integration tests: tagged `@Tag("integration")`, hit `cvmfs-stratum-one.cern.ch`
- Surefire runs unit tests, Failsafe runs integration tests
