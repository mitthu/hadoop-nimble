package org.apache.hadoop.hdfs.server.nimble;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.NNStorage;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;


/* Main class */
public class NimbleTester {
    static Logger logger = Logger.getLogger(NimbleTester.class);

    /* For development and testing only */
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, InvalidKeySpecException, SignatureException, InvalidParameterSpecException, DecoderException, URISyntaxException, InvalidAlgorithmParameterException {
        logger.setLevel(Level.DEBUG);
        logger.debug("Run Nimble workflow for testing");
        Configuration conf = new Configuration();

        keyPairTest();
        try (NimbleAPI n = new NimbleAPI(conf)) {
            test_workflow(n);
        }
        format_storage(conf);
//        test_TMCS();
    }

    /**
     * Nimble-related metadata
     */
    public static void format_storage(Configuration conf) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        logger.info("Formatting storage");
        FSImage image = new FSImage(conf);
        NNStorage storage = image.getStorage();
        TMCS.format(conf); // otherwise, the call below will fail
        storage.format();

        // Log storage dirs.
        for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext();) {
            Storage.StorageDirectory sd = it.next();
            logger.info(sd);
        }
    }

    /**
     * Test TMCS Nimble API
     */
    public static void test_TMCS() throws IOException {
        logger.info("Testing TMCS");
        TMCS tmcs = TMCS.getInstance();
        TMCS.format(new Configuration());
        NimbleOp op;

        tmcs.initialize("fsimage".getBytes());
        tmcs.increment("tag_1".getBytes());
        tmcs.increment("tag_2".getBytes());

        op = tmcs.latest();
        assert Arrays.equals(op.tag, "tag_2".getBytes());
        assert op.counter == 2;

        logger.info("Successfully tested TMCS");
    }

    /**
     * https://stackoverflow.com/a/11353993
     * https://metamug.com/article/security/sign-verify-digital-signature-ecdsa-java.html
     */
    public static void keyPairTest() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException, InvalidKeySpecException {
        final String SPEC = "secp256k1";
        final String ALGO = "SHA256withECDSA";
        final String msg = "Some random plaintext.";

        // Init
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("EC");
        keyPairGen.initialize(new ECGenParameterSpec(SPEC), new SecureRandom());

        // Generate
        KeyPair pair = keyPairGen.generateKeyPair();
        PublicKey pub = pair.getPublic();
        PrivateKey priv = pair.getPrivate();
        logger.info("Public: " + NimbleUtils.URLEncode(pub.getEncoded()) + " format=" + pub.getFormat()
                + " algo=" + pub.getAlgorithm());
        logger.info("Private: " + NimbleUtils.URLEncode(priv.getEncoded()) + " format=" + priv.getFormat()
                + " algo=" + priv.getAlgorithm());

        // Sign
        Signature ecdsa = Signature.getInstance(ALGO);
        ecdsa.initSign(priv);
        ecdsa.update(msg.getBytes(StandardCharsets.UTF_8));
        byte sign[] = ecdsa.sign();
        logger.info("Signature ("+ sign.length +"): " + NimbleUtils.URLEncode(sign));
        logger.info("Message: " + msg);
        ecdsa = Signature.getInstance(ALGO);
        ecdsa.initSign(priv);
        ecdsa.update(msg.getBytes(StandardCharsets.UTF_8));
        sign = ecdsa.sign();
        logger.info("Again Signature ("+ sign.length +"): " + NimbleUtils.URLEncode(sign));
        logger.info("Again Message: " + msg);

        // Parse Keys
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pub.getEncoded());
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(priv.getEncoded());
        KeyFactory kf = KeyFactory.getInstance("EC");

        PublicKey genPublicKey = kf.generatePublic(publicKeySpec);
        PrivateKey genPrivateKey = kf.generatePrivate(privateKeySpec);
        logger.info("Public: " + NimbleUtils.URLEncode(genPublicKey.getEncoded()) + " format=" + genPublicKey.getFormat()
                + " algo=" + genPublicKey.getAlgorithm());
        logger.info("Private (load): " + NimbleUtils.URLEncode(genPrivateKey.getEncoded()) + " format=" + genPrivateKey.getFormat()
                + " algo=" + genPrivateKey.getAlgorithm());
        logger.info("Compare Private key: generated == loaded is " + Arrays.equals(priv.getEncoded(), genPrivateKey.getEncoded()));

        // Verify Signature w/ loaded public key
        Signature sg = Signature.getInstance(ALGO);
        sg.initVerify(genPublicKey);
        sg.update(msg.getBytes(StandardCharsets.UTF_8));
        logger.info("Verify signature w/ loaded public key: " + sg.verify(sign));

        // Re-Sign w/ loaded private key
        Signature ecdsaLoaded = Signature.getInstance(ALGO);
        ecdsaLoaded.initSign(genPrivateKey);
        ecdsaLoaded.update(msg.getBytes(StandardCharsets.UTF_8));
        byte signLoad[] = ecdsaLoaded.sign();
        logger.info("Signature ("+ sign.length +"): " + NimbleUtils.URLEncode(signLoad));
    }

    /**
     * Test TMCS Nimble API
     */
    public static void test_workflow(NimbleAPI n) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException, SignatureException, InvalidKeyException, DecoderException, URISyntaxException {
        logger.info("Testing NimbleAPI");
        NimbleServiceID id = NimbleUtils.loadAndValidateNimbleInfo(n);
        assert id.handle != null;

        // Step 0: Sanity checks
//        test_verify_1(n);
//        test_verify_2(n);
        test_verify_3(n);

        // Step 1: NewCounter Request
        NimbleOp op;
        op = n.newCounter(id, "some-tag-value".getBytes());
        logger.info(op.toString());
        // Print byte representation of to be hashed & signed
        //logger.info(NimbleOp.toNimbleStringRepr(op.toString().getBytes()));
        logger.info("NewCounter (verify): " + op.verify());

        // Step 2: Read Latest w/ Nonce
        op = n.readLatest(id);
        logger.info("ReadLatest (verify): " + op.verify());

        // Step 3: Increment Counter
        op = n.incrementCounter(id, "tag_1".getBytes(), 1);
        logger.info("IncrementCounter " + "tag=tag_1 counter=1" + " (verify): " + op.verify());

        op = n.incrementCounter(id, "tag_2".getBytes(), 2);
        logger.info("IncrementCounter " + "tag=tag_2 counter=2" + " (verify): " + op.verify());

        // Step 4: Read Latest w/ Nonce
        op = n.readLatest(id);

        assert op.tag == "tag_2".getBytes();
        assert op.counter == 2;
        logger.info("Verify readLatest: " + op.verify());
        logger.info("Successfully tested NimbleAPI");
    }

   /**
    * From output of light_client_rest
    */
    public static void test_verify_1(NimbleAPI n) throws NoSuchAlgorithmException, NoSuchProviderException, IOException, InvalidParameterSpecException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        NimbleServiceID id = n.getServiceID();
        String d_str = "cix-tx9U14vwW-U50K-l9uRe17FWZGX3VqvvII4nIg0",
                s_str = "0Cku2NbVNZAC6OOQIHCxDFEnff7ManHLu1hlrDfurNmAXiFUsQ8ddIvM4i5-oG7JsyCbEstNUWouAKdD8x-f5A";
        byte[]  d   = NimbleUtils.URLDecode(d_str), s = NimbleUtils.URLDecode(s_str);
        boolean res = id.verifySignature(s, d);
        logger.info("pk = " + NimbleOp.toNimbleStringRepr(id.publicKey));

        logger.info("digest = " + NimbleOp.toNimbleStringRepr(d));
        logger.info("signature = " + NimbleOp.toNimbleStringRepr(s));
        logger.info("digest = " + new String(d));
        logger.info("signature = " + new String(s));

        logger.info("Signature verification: " + res);
    }

    /**
     * From ledger/src/signature.rs: test_compressed_pk_and_raw_signature_encoding()
     */
    public static void test_verify_2(NimbleAPI n) throws DecoderException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException, SignatureException, InvalidKeyException, NimbleError {
        String
                pk = "03A60909370C9CCB5DD3B909654AE158E21C4EE35C7A291C7197F38E22CA95B858",
                r = "3341835E0BA33047E0B472F5622B157ED5879085213A1777963571220E48BF0F",
                s = "8B630A0251F157CAB579FD3D589969A92CCC75C9B5058E2BF77F7038D352DF10",
                m = "0000000000000000000000000000000000000000000000000000000000000000";
        byte[]
                pk_b = Hex.decodeHex(pk.toCharArray()),
                r_b = Hex.decodeHex(r.toCharArray()),
                s_b = Hex.decodeHex(s.toCharArray()),
                m_b = Hex.decodeHex(m.toCharArray()),
                sig_b = new byte[r_b.length + s_b.length];
        System.arraycopy(r_b, 0, sig_b, 0, r_b.length);
        System.arraycopy(s_b, 0, sig_b, r_b.length, s_b.length);

        NimbleServiceID service = new NimbleServiceID(new byte[]{}, pk_b, null, null, null);
        logger.info(service);
        boolean signature = service.verifySignature(sig_b, m_b);
        logger.info("Signature: " + signature);
    }

    /**
     * Test signature verification from static content.
     */
    public static void test_verify_3(NimbleAPI n) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException, SignatureException, InvalidKeyException, NimbleError {
        // New Counter Response
        String pkStr = "BOFFzset0458QLqmae92__sjv_zTwJRZH9P0PeI3TvP7Kj1j_J7w-LxgwkkL7doHQtuUDP0BxM6vnvz9O8w0WKY",
               msgStr = "ziFukI0-0-WnvW_cgQ7bS6TGG6gkUr5DzneENpexTr0.NX5JoOCjT-u4aiLKJq9IWQ.AAAAAAAAAAA.VDqSDZA1EZfBg-KCcI5hFQ",
               sigStr = "jDSRYTmCRUMuRPaJ4XZE5ZkjFgXK0Q_NbImwL-QuRYKqh2jwYhVOshhcb9UJwsW_n2Ey9ct-gBUUqVGP-F9Ang";
        byte[] pk = NimbleUtils.URLDecode(pkStr),
               msg = msgStr.getBytes(),
               sig = NimbleUtils.URLDecode(sigStr);

        // Mock service ID and create verify signature
        NimbleServiceID service = new NimbleServiceID(new byte[]{}, pk, null, null, null);
        logger.info(service);
        boolean signature = service.verifySignature(sig, msg);
        logger.info("Verification Result: " + signature);
    }
}
