package com.molina.cvmfs.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CvmfsExceptionTest {

    @Test
    void messageOnly() {
        var ex = new CvmfsException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messageWithCause() {
        var cause = new RuntimeException("root cause");
        var ex = new CvmfsException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isException() {
        assertInstanceOf(Exception.class, new CvmfsException("x"));
    }
}
