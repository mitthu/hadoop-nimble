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

    /**
     * Format:
     * MsgType.NimbleID.Handle.Counter.Tag
     *
     * Example encoding:
     * AwAAAAAAAAA.C9JtOpmXyBd-anyeBbhr5RZ0ac2urm5Nt-z_C88wfvU.HKL9dcyf4dsrhskxUHeF-g.AQAAAAAAAAA.dGFnXzE
     */
    @Override
    public String toString() {
        return String.format("%s.%s.%s.%s.%s",
                NimbleUtils.URLEncode(longToBytes(TYPE_INCREMENT_COUNTER)), // Message Type
                NimbleUtils.URLEncode(id.identity),
                NimbleUtils.URLEncode(handle),
                NimbleUtils.URLEncode(longToBytes(counter)),
                NimbleUtils.URLEncode(tag)
        );
    }
}
