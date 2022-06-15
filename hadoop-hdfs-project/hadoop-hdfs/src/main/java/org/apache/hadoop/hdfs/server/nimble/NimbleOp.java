package org.apache.hadoop.hdfs.server.nimble;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.ArrayList;

/* Captures responses */
abstract class NimbleOp {
    // From Request
    public NimbleServiceID id;
    public byte[]          handle;

    // From Response
    public byte[] tag;
    public int    counter;
    public byte[] signature;

    // Verify signature
    public boolean verify() {
        String str = this.toString();
        try {
            return id.verifySignature(signature, str.getBytes());
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
}
