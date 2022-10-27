package org.apache.hadoop.hdfs.server.nimble;

import java.io.IOException;
import java.security.SignatureException;

/* Error handling */
public class NimbleError extends IOException {
    public NimbleError(String msg) {
        super(msg);
    }

    public NimbleError(SignatureException e) {
        this("Cannot calculate signature: " + e);
    }
}
