package com.molina.cvmfs.reflog;

public record RefEntry(String hash, RefType refType, long timestamp) {}
