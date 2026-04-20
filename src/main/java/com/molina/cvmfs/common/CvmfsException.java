package com.molina.cvmfs.common;

public class CvmfsException extends Exception {
    public CvmfsException(String message) {
        super(message);
    }

    public CvmfsException(String message, Throwable cause) {
        super(message, cause);
    }
}
