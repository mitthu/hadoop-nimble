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

    /**
     * Format:
     * MsgType.NimbleID.Handle.Counter.Tag
     *
     * Example encoding:
     * AQAAAAAAAAA.C9JtOpmXyBd-anyeBbhr5RZ0ac2urm5Nt-z_C88wfvU.U3qYAXnAaH97OpiRc1XTCA.AAAAAAAAAAA.c29tZS10YWctdmFsdWU
     */
    @Override
    public String toString() {
        return String.format("%s.%s.%s.%s.%s",
                NimbleUtils.URLEncode(longToBytes(TYPE_NEW_COUNTER)), // Message Type
                NimbleUtils.URLEncode(id.identity),
                NimbleUtils.URLEncode(handle),
                NimbleUtils.URLEncode(longToBytes(counter)),
                NimbleUtils.URLEncode(tag)
        );
    }
}
