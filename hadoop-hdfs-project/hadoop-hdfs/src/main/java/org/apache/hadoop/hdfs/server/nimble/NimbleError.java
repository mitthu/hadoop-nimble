package org.apache.hadoop.hdfs.server.nimble;

import java.io.IOException;

/* Error handling */
public class NimbleError extends IOException {
    public NimbleError(String msg) {
        super(msg);
    }
}
