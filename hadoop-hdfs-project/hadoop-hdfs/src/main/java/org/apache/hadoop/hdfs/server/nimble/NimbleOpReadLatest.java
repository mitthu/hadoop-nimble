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

    /**
     * Format:
     * MsgType.NimbleID.Handle.Counter.Tag.Nonce
     *
     * Example encoding:
     * BQAAAAAAAAA.C9JtOpmXyBd-anyeBbhr5RZ0ac2urm5Nt-z_C88wfvU.HKL9dcyf4dsrhskxUHeF-g.AAAAAAAAAAA.c29tZS10YWctdmFsdWU.Cl9crZbg3dwS9W30jT0j2A
     */
    @Override
    public String toString() {
        return String.format("%s.%s.%s.%s.%s.%s",
                NimbleUtils.URLEncode(longToBytes(TYPE_READ_COUNTER)), // Message Type
                NimbleUtils.URLEncode(id.identity),
                NimbleUtils.URLEncode(handle),
                NimbleUtils.URLEncode(longToBytes(counter)),
                NimbleUtils.URLEncode(tag),
                NimbleUtils.URLEncode(nonce)
        );
    }
}
