package org.apache.hadoop.hdfs.server.nimble;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.*;
import java.util.ArrayList;

/* Captures responses */
abstract class NimbleOp {
    // From Request
    public NimbleServiceID id;
    public byte[]          handle;

    // From Response
    public byte[] tag;
    public int  counter;
    public byte[] signature;

    // Message Types
    public static final long TYPE_NEW_COUNTER = 1;
    public static final long TYPE_INCREMENT_COUNTER = 3;
    public static final long TYPE_READ_COUNTER = 5;

    // Verify signature
    public boolean verify() throws NimbleError {
        String msg = this.toString();
        try {
            return id.verifySignature(signature, msg.getBytes());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Converts byte[]{10, 23, ...} to String("[10, 23, ...]")
     *
     * @param bs Input byte sequence
     * @return String representation
     */
    public static String toNimbleStringRepr(byte[] bs) {
        if (NimbleUtils.READABLE_LOG_OPERATIONS)
            return NimbleUtils.URLEncode(bs); // Note: verification will fail

        ArrayList<Integer> as = new ArrayList<>(bs.length);
        for (Byte b : bs) {
            as.add(b & 0xFF);
        }
        return as.toString();
    }

    /**
     * Convert long to byte buffer (use little endian)
     */
    public static byte[] longToBytes(long i) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(i);
        return buffer.array();
    }
}
