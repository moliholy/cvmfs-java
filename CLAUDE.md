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
- `fetcher/Fetcher` - HTTP downloads with multi-format decompression (zlib, zstd, lz4), mirror failover, proxy, retry with exponential backoff
- `fetcher/Cache` - local 2-level hex-prefix cache with TTL, quota enforcement, negative caching
- `directoryentry/DirectoryEntry` - file/dir metadata from catalog DB
- `history/History` - SQLite history DB with tagged revisions
- `whitelist/Whitelist` - parses `.cvmfswhitelist` with fingerprints and expiry
- `rootfile/RootFile` - abstract base for signed root files (manifest, whitelist)
- `common/CvmfsException` - single exception type for all CVMFS errors
- `common/FileLike` - interface for polymorphic file access (regular and chunked)
- `common/RegularFile` - `FileLike` wrapper for regular files with offset-based reading
- `common/ChunkedFile` - `FileLike` for chunked files with prefetch support
- `dns/DnsDiscovery` - DNS TXT record lookup for mirror discovery
- `geo/GeoSorter` - geo-location based mirror sorting via API
- `blacklist/Blacklist` - certificate fingerprint and revision blocking
- `reflog/Reflog` - SQLite-backed reference log with typed entries
- `breadcrumb/Breadcrumb` - catalog hash caching for fast remount

## Key patterns

- Records: `PathHash`, `RevisionTag`, `CatalogReference`, `DirectoryEntryWrapper`, `RefEntry`
- Enums: `ContentHashTypes` (SHA1, RIPEMD160, SHA256, SHAKE128), `RefType` (CATALOG, CERTIFICATE, HISTORY, META_INFO)
- `Optional` returned from lookups (no nulls at API boundary)
- `java.time.Instant` for timestamps (not `java.util.Date`)
- `java.nio.file.Path` for file paths (not `java.io.File`)
- `AutoCloseable` on `DatabaseObject`, `Catalog`, `History`, `Reflog`, `RegularFile`, `ChunkedFile`
- SQL injection prevented via `PreparedStatement` everywhere
- Multi-source fetching with mirror failover and exponential backoff retry
- Content-addressed data objects never expire in cache; metadata uses TTL

## Tests

- Unit tests: `src/test/java/` (no network)
- Integration tests: tagged `@Tag("integration")`, hit `cvmfs-stratum-one.cern.ch`
- Surefire runs unit tests, Failsafe runs integration tests
