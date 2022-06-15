package org.apache.hadoop.hdfs.server.nimble;

import java.util.Map;

/* Captures response from IncrementCounter */
class NimbleOpIncrementCounter extends NimbleOp {
    public NimbleOpIncrementCounter(NimbleServiceID id, byte[] handle, byte[] tag, int expected_counter, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.tag = tag;
        this.counter = expected_counter;
        this.signature = NimbleUtils.URLDecode((String) json.get("Signature"));
    }

    @Override
    public String toString() {
        return String.format("IncrementCounter id: %s, handle = %s, tag = %s, counter = %d",
                NimbleOp.toNimbleStringRepr(id.identity),
                NimbleOp.toNimbleStringRepr(handle),
                NimbleOp.toNimbleStringRepr(tag),
                counter
        );
    }
}
