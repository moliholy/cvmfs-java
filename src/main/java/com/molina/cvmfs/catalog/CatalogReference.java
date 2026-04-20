package com.molina.cvmfs.catalog;

import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.repository.Repository;

public record CatalogReference(String rootPath, String catalogHash, int catalogSize) {
    public Catalog retrieveFrom(Repository repo) throws CvmfsException {
        return repo.retrieveCatalog(catalogHash);
    }
}
