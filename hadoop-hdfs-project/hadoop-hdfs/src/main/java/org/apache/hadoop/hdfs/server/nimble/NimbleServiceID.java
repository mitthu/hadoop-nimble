package org.apache.hadoop.hdfs.server.nimble;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;

/* NimbleServiceID */
public final class NimbleServiceID {
    final String SIGN_SPEC = "secp256k1";
    final String SIGN_ALGO = "SHA256withECDSA";

    public byte[] identity;
    public byte[] publicKey;
    public byte[] handle;

    // Signing keys
    private PublicKey signPublicKey;
    private PrivateKey signPrivateKey;

    private PublicKey pk;

    public NimbleServiceID(byte[] identity, byte[] publicKey, byte[] handle, byte[] signPublicKey, byte[] signPrivateKey) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        this.identity = identity;
        this.publicKey = publicKey;
        this.handle = handle;
        this.pk = parsePublicKey(publicKey);

        // Assign signing keys. These keys are used for signing the tag stored in Nimble.
        KeyFactory kf = KeyFactory.getInstance("EC");
        if (signPublicKey != null)
            this.signPublicKey = kf.generatePublic(new X509EncodedKeySpec(signPublicKey));
        if (signPrivateKey != null)
            this.signPrivateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(signPrivateKey));
    }

    public NimbleServiceID(String identity, String publicKey, String handle) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        this(NimbleUtils.URLDecode(identity), NimbleUtils.URLDecode(publicKey), null, null, null);
        if (handle != null) {
            this.handle = NimbleUtils.URLDecode(handle);
        }
    }

    public NimbleServiceID(String identity, String publicKey, String handle, String signPublicKey, String signPrivateKey)
            throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        this(NimbleUtils.URLDecode(identity), NimbleUtils.URLDecode(publicKey), null,
             NimbleUtils.URLDecode(signPublicKey), NimbleUtils.URLDecode(signPrivateKey));
        if (handle != null) {
            this.handle = NimbleUtils.URLDecode(handle);
        }
    }

    @Override
    public String toString() {
        String pub = (this.signPublicKey != null) ? NimbleUtils.URLEncode(signPublicKey.getEncoded()) : "null";
        String priv = (this.signPrivateKey != null) ? NimbleUtils.URLEncode(signPrivateKey.getEncoded()) : "null";

        return "NimbleServiceID{" +
                "identity=" + NimbleUtils.URLEncode(identity) +
                ", publicKey=" + NimbleUtils.URLEncode(publicKey) +
                ", handle=" + NimbleUtils.URLEncode(handle) +
                ", signPublicKey=" + pub +
                ", signPrivateKey=" + priv +
                '}';
    }

    public void generateSigningKeys() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        // Init
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("EC");
        keyPairGen.initialize(new ECGenParameterSpec(SIGN_SPEC), new SecureRandom());

        // Generate
        KeyPair pair = keyPairGen.generateKeyPair();
        this.signPublicKey = pair.getPublic();
        this.signPrivateKey = pair.getPrivate();
    }

    public Signature getSignature() throws NimbleError {
        if (this.signPrivateKey == null)
            throw new NimbleError("Private key for signing is not set");

        try {
            Signature ecdsa = Signature.getInstance(SIGN_ALGO);
            ecdsa.initSign(this.signPrivateKey);
            return ecdsa;
        } catch (Exception e) {
            throw new NimbleError("Cannot sign message: " + e);
        }
    }

    public Signature verifySignature() throws NimbleError {
        if (this.signPublicKey == null)
            throw new NimbleError("Public key for signing is not set");

        try {
            Signature sg = Signature.getInstance(SIGN_ALGO);
            sg.initVerify(signPublicKey);
            return sg;
        } catch (Exception e) {
            throw new NimbleError("Cannot verify signature on tag: " + e);
        }
    }

    public boolean canSign() {
        return signPrivateKey != null && signPublicKey != null;
    }

    public byte[] getSignPublicKey() {
        return signPublicKey.getEncoded();
    }

    public byte[] getSignPrivateKey() {
        return signPrivateKey.getEncoded();
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

    public static PublicKey parsePublicKey(byte[] pubKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable
                .getParameterSpec("prime256v1");
        ECNamedCurveSpec params = new ECNamedCurveSpec("prime256v1",
                spec.getCurve(), spec.getG(), spec.getN());

        ECPoint point = ECPointUtil.decodePoint(params.getCurve(), pubKey);
        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, params);
        KeyFactory kf = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider());
        ECPublicKey pk = (ECPublicKey) kf.generatePublic(pubKeySpec);
        return pk;
    }

    /**
     * Verify the signature on the given message.
     *
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
        Signature sg = Signature.getInstance("SHA256withECDSA", new BouncyCastleProvider());

        // Verification
        sg.initVerify(pk);
        sg.update(msg);
        return sg.verify(NimbleServiceID.toANS1Signature(signature));
    }

    /**
     * Encode raw signature into ANS.1 format
     *
     * ANS.1 Format => 0x30 b1 0x02 b2 (vr) 0x02 b3 (vs)
     * where,
     * b1 is a single byte value, equal to the length, in bytes, of the remaining list of bytes
     * b2 is a single byte value, equal to the length, in bytes, of (vr);
     * b3 is a single byte value, equal to the length, in bytes, of (vs);
     * (vr) is the signed big-endian encoding of the value "r", of minimal length;
     * (vs) is the signed big-endian encoding of the value "s", of minimal length.
     *
     * Taken from:
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
