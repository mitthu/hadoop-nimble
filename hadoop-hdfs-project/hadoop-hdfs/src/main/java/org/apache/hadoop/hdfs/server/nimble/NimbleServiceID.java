package org.apache.hadoop.hdfs.server.nimble;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;

/* NimbleServiceID */
final class NimbleServiceID {
    public byte[] identity;
    public byte[] publicKey;
    public byte[] handle;

    private ECPublicKey pk;

    public NimbleServiceID(byte[] identity, byte[] publicKey, byte[] handle) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        this.identity = identity;
        this.publicKey = publicKey;
        this.handle = handle;
        setECPublicKey();
    }

    public NimbleServiceID(String identity, String publicKey, String handle) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        this(NimbleUtils.URLDecode(identity), NimbleUtils.URLDecode(publicKey), null);
        if (handle != null) {
            this.handle = NimbleUtils.URLDecode(handle);
        }
    }

    @Override
    public String toString() {
        return "NimbleServiceID{" +
                "identity=" + Arrays.toString(identity) +
                ", publicKey=" + Arrays.toString(publicKey) +
                ", handle=" + Arrays.toString(handle) +
                '}';
    }

    public boolean equals(NimbleServiceID other) {
        return Arrays.equals(this.identity, other.identity) &&
                Arrays.equals(this.publicKey, other.publicKey) &&
                Arrays.equals(this.handle, other.handle);
    }

    public boolean valid() {
        return this.identity.length != 0 &&
                this.publicKey.length != 0 &&
                this.handle.length != 0;
    }

    /**
     * Parse raw bytes of public key into Java compatible public key.
     * <p>
     * https://stackoverflow.com/a/56170785
     *
     * @throws NoSuchAlgorithmException
     * @throws InvalidParameterSpecException
     * @throws InvalidKeySpecException
     */
    private void setECPublicKey() throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        byte[] x = Arrays.copyOfRange(publicKey, 0, publicKey.length / 2);
        byte[] y = Arrays.copyOfRange(publicKey, publicKey.length / 2, publicKey.length);

        // Construct ECParameterSpec
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);

        // Generate ECPublicKey
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPoint    w  = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        this.pk = (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(w, spec));
    }

    /**
     * Verify the signature on the given message.
     * <p>
     * https://etzold.medium.com/elliptic-curve-signatures-and-how-to-use-them-in-your-java-application-b88825f8e926
     *
     * @param signature Represented as a pair of (r, s)
     * @param msg       The message to be verified
     * @return Verification is successful or not
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     */
    public boolean verifySignature(byte[] signature, byte[] msg) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
        // Available algorithms:
        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyFactory
        Signature sg = Signature.getInstance("SHA256withECDSA", "SunEC");

        // Verification
        sg.initVerify(pk);
        sg.update(msg);
        return sg.verify(NimbleServiceID.toANS1Signature(signature));
    }

    /**
     * Encode raw signature into ANS.1 format
     * <p>
     * ANS.1 Format => 0x30 b1 0x02 b2 (vr) 0x02 b3 (vs)
     * where,
     * b1 is a single byte value, equal to the length, in bytes, of the remaining list of bytes
     * b2 is a single byte value, equal to the length, in bytes, of (vr);
     * b3 is a single byte value, equal to the length, in bytes, of (vs);
     * (vr) is the signed big-endian encoding of the value "r", of minimal length;
     * (vs) is the signed big-endian encoding of the value "s", of minimal length.
     * <p>
     * https://stackoverflow.com/a/56839652
     * https://crypto.stackexchange.com/a/1797
     *
     * @param rawsig Pair of (r, s)
     * @return ANS.1 encoded signature
     */
    public static byte[] toANS1Signature(byte[] rawsig) {
        byte[] r   = new BigInteger(1, Arrays.copyOfRange(rawsig, 0, 32)).toByteArray();
        byte[] s   = new BigInteger(1, Arrays.copyOfRange(rawsig, 32, 64)).toByteArray();
        byte[] der = new byte[6 + r.length + s.length];
        der[0] = 0x30; // Tag of signature object
        der[1] = (byte) (der.length - 2); // Length of signature object
        int o = 2;
        der[o++] = 0x02; // Tag of ASN1 Integer
        der[o++] = (byte) r.length; // Length of first signature part
        System.arraycopy(r, 0, der, o, r.length);
        o += r.length;
        der[o++] = 0x02; // Tag of ASN1 Integer
        der[o++] = (byte) s.length; // Length of second signature part
        System.arraycopy(s, 0, der, o, s.length);
        return der;
    }
}
