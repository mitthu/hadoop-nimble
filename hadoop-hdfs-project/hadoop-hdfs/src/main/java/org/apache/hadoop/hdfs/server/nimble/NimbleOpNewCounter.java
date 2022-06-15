package org.apache.hadoop.hdfs.server.nimble;

import java.util.Map;

/* Captures response from IncrementCounter */
class NimbleOpNewCounter extends NimbleOp {
    public NimbleOpNewCounter(NimbleServiceID id, byte[] handle, byte[] tag, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.tag = tag;
        this.counter = 0;
        this.signature = NimbleUtils.URLDecode((String) json.get("Signature"));
    }

    @Override
    public String toString() {
        return String.format("NewCounter id: %s, handle = %s, tag = %s, counter = %d",
                NimbleOp.toNimbleStringRepr(id.identity),
                NimbleOp.toNimbleStringRepr(handle),
                NimbleOp.toNimbleStringRepr(tag),
                counter
        );
    }
}
