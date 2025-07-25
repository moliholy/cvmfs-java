# cvmfs-java

A Java library for interacting with [CernVM-FS (CVMFS)](https://github.com/cvmfs/cvmfs), a scalable, reliable and low-maintenance software distribution service.

## Features

- Read and verify CVMFS repositories.
- Fetch and validate manifests, catalogs, certificates, and whitelists.
- Access repository history and revisions.
- Support for both HTTP and local file repositories.

## Installation

Clone the repository and build with Maven:

```sh
git clone https://github.com/yourusername/cvmfs-java.git
cd cvmfs-java
mvn clean install
```

## Usage

```java
import com.molina.cvmfs.repository.Repository;

public class Example {
    public static void main(String[] args) throws Exception {
        // Initialize repository from a source (URL or local path) and cache directory
        Repository repo = new Repository("http://your.cvmfs.repo", "/tmp/cvmfs-cache");

        // Get repository name
        System.out.println("Repository: " + repo.getFqrn());

        // Verify repository signature
        boolean valid = repo.verify("/path/to/public.pem");
        System.out.println("Signature valid: " + valid);

        // Access manifest and catalogs
        System.out.println("Manifest revision: " + repo.getManifest().getRevision());
    }
}
```

## Project Structure

- `com.molina.cvmfs.repository.Repository` – Main entry point for repository operations
- `com.molina.cvmfs.catalog.Catalog` – Catalog file handling
- `com.molina.cvmfs.manifest.Manifest` – Manifest file parsing and validation
- `com.molina.cvmfs.certificate.Certificate` – Certificate management
- `com.molina.cvmfs.whitelist.Whitelist` – Whitelist signature verification

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- The original [CernVM-FS project](https://github.com/cvmfs/cvmfs).
- All contributors and maintainers.
