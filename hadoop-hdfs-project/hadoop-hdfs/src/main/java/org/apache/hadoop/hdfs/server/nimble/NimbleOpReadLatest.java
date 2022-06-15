package org.apache.hadoop.hdfs.server.nimble;

import java.util.Map;

/* Captures response from ReadLatest */
public class NimbleOpReadLatest extends NimbleOp {
    // From Request
    public byte[] nonce;

    public NimbleOpReadLatest(NimbleServiceID id, byte[] handle, byte[] nonce, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.nonce = nonce;
        this.tag = NimbleUtils.URLDecode((String) json.get("Tag"));
        this.counter = (Integer) json.get("Counter");
        this.signature = NimbleUtils.URLDecode((String) json.get("Signature"));
    }

    @Override
    public String toString() {
        return String.format("ReadCounter id: %s, handle = %s, tag = %s, counter = %d, nonce = %s",
                NimbleOp.toNimbleStringRepr(id.identity),
                NimbleOp.toNimbleStringRepr(handle),
                NimbleOp.toNimbleStringRepr(tag),
                counter,
                NimbleOp.toNimbleStringRepr(nonce)
        );
    }
}
