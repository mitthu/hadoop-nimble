package org.apache.hadoop.hdfs;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.NNStorage;
import org.apache.hadoop.thirdparty.com.google.common.io.BaseEncoding;
import org.apache.hadoop.util.JsonSerialization;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;

/* Error handling */
class NimbleError extends IOException {
    public NimbleError(String msg) {
        super(msg);
    }
}

/* NimbleServiceID */
final class NimbleServiceID {
    public byte[] identity;
    public byte[] publicKey;

    private ECPublicKey pk;

    public NimbleServiceID(byte[] identity, byte[] publicKey) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        this.identity = identity;
        this.publicKey = publicKey;
        setECPublicKey();
    }

    public NimbleServiceID(String identity, String publicKey) throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        this(Nimble.URLDecode(identity), Nimble.URLDecode(publicKey));
    }

    @Override
    public String toString() {
        return "NimbleServiceID{" +
                "identity='" + Nimble.URLEncode(identity) + '\'' +
                ", publicKey='" + Nimble.URLEncode(publicKey) + '\'' +
                '}';
    }

    /**
     * Parse raw bytes of public key into Java compatible public key.
     *
     * https://stackoverflow.com/a/56170785
     *
     * @throws NoSuchAlgorithmException
     * @throws InvalidParameterSpecException
     * @throws InvalidKeySpecException
     */
    private void setECPublicKey() throws NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException {
        byte[] x = Arrays.copyOfRange(publicKey, 0, publicKey.length/2);
        byte[] y = Arrays.copyOfRange(publicKey, publicKey.length/2, publicKey.length);

        // Construct ECParameterSpec
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);

        // Generate ECPublicKey
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPoint w = new ECPoint(new BigInteger(1,x), new BigInteger(1,y));
        this.pk = (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(w, spec));
    }

    /**
     * Verify the signature on the given message.
     *
     * https://etzold.medium.com/elliptic-curve-signatures-and-how-to-use-them-in-your-java-application-b88825f8e926
     *
     * @param signature     Represented as a pair of (r, s)
     * @param msg           The message to be verified
     * @return              Verification is successful or not
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     */
    public boolean verifySignature(byte[] signature, byte[] msg) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
        // Available algorithms:
        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyFactory
        Signature sg = Signature.getInstance("SHA256withECDSA","SunEC");

        // Verification
        sg.initVerify(pk);
        sg.update(msg);
        return sg.verify(NimbleServiceID.toANS1Signature(signature));
    }

    /**
     * Encode raw signature into ANS.1 format
     *
     * ANS.1 Format => 0x30 b1 0x02 b2 (vr) 0x02 b3 (vs)
     *  where,
     *      b1 is a single byte value, equal to the length, in bytes, of the remaining list of bytes
     *      b2 is a single byte value, equal to the length, in bytes, of (vr);
     *      b3 is a single byte value, equal to the length, in bytes, of (vs);
     *      (vr) is the signed big-endian encoding of the value "r", of minimal length;
     *      (vs) is the signed big-endian encoding of the value "s", of minimal length.
     *
     * https://stackoverflow.com/a/56839652
     * https://crypto.stackexchange.com/a/1797
     *
     * @param rawsig    Pair of (r, s)
     * @return          ANS.1 encoded signature
     */
    public static byte[] toANS1Signature(byte[] rawsig) {
        byte[] r = new BigInteger(1,Arrays.copyOfRange(rawsig,0,32)).toByteArray();
        byte[] s = new BigInteger(1,Arrays.copyOfRange(rawsig,32,64)).toByteArray();
        byte[] der = new byte[6+r.length+s.length];
        der[0] = 0x30; // Tag of signature object
        der[1] = (byte)(der.length-2); // Length of signature object
        int o = 2;
        der[o++] = 0x02; // Tag of ASN1 Integer
        der[o++] = (byte)r.length; // Length of first signature part
        System.arraycopy (r,0, der,o, r.length);
        o += r.length;
        der[o++] = 0x02; // Tag of ASN1 Integer
        der[o++] = (byte)s.length; // Length of second signature part
        System.arraycopy (s,0, der,o, s.length);
        return der;
    }
}


/* Captures responses */
abstract class NimbleOp {
    // From Request
    public NimbleServiceID id;
    public byte[] handle;

    // From Response
    public byte[] tag;
    public int counter;
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
     * @param bs    Input byte sequence
     * @return      String representation
     */
    public static String toNimbleStringRepr(byte[] bs) {
        ArrayList<Integer> as = new ArrayList<>(bs.length);
        for (Byte b: bs) {
            as.add(b & 0xFF);
        };
        return as.toString();
    }
}

/* Captures response from IncrementCounter */
class NimbleOpNewCounter extends NimbleOp {
    public NimbleOpNewCounter(NimbleServiceID id, byte[] handle, byte[] tag, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.tag = tag;
        this.counter = 0;
        this.signature = Nimble.URLDecode((String) json.get("Signature"));
    }

    @Override
    public String toString() {
        return String.format("NewCounter id: %s, handle = %s, tag = %s, counter = %d",
                NimbleOp.toNimbleStringRepr(id.identity),
                NimbleOp.toNimbleStringRepr(handle),
                NimbleOp.toNimbleStringRepr(tag),
                counter
        );
    }
}


/* Captures response from ReadLatest */
class NimbleOpReadLatest extends NimbleOp {
    // From Request
    public byte[] nonce;

    public NimbleOpReadLatest(NimbleServiceID id, byte[] handle, byte[] nonce, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.nonce = nonce;
        this.tag = Nimble.URLDecode((String) json.get("Tag"));
        this.counter = (Integer) json.get("Counter");
        this.signature = Nimble.URLDecode((String) json.get("Signature"));
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


/* Captures response from IncrementCounter */
class NimbleOpIncrementCounter extends NimbleOp {
    public NimbleOpIncrementCounter(NimbleServiceID id, byte[] handle, byte[] tag, int expected_counter, Map<?, ?> json) {
        this.id = id;
        this.handle = handle;
        this.tag = tag;
        this.counter = expected_counter;
        this.signature = Nimble.URLDecode((String) json.get("Signature"));
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


/* Store & load configuration */
class NimbleConf {
    public static byte[]          handle;
    public static NimbleServiceID id;

    public synchronized static void initialize(Configuration conf) {
        if (handle != null) {
            return; // already initialized
        }
        handle = "namenode".getBytes(StandardCharsets.UTF_8);
    }

    public static void initialize() {
        initialize(new Configuration());
    }
}


/* Main class */
public class Nimble implements java.io.Closeable {
    static Logger logger = Logger.getLogger(Nimble.class);

    public static final String NIMBLEURI_KEY            = "fs.nimbleURI";
    public static final String NIMBLEURI_DEFAULT        = "http://[::1]:8082/";
    public static final String NIMBLE_INFO              = "NIMBLE";
    public static final String NIMBLE_FSIMAGE_EXTENSION = ".nimble";

    private URI nimble_rest_uri;
    private CloseableHttpClient httpClient;
    private NimbleServiceID serviceID;

    public static byte[] getNonce() {
        return RandomUtils.nextBytes(16);
    }

    public static String URLEncode(byte[] value) {
        return BaseEncoding.base64Url().omitPadding().encode(value);
    }

    public static byte[] URLDecode(String value) {
        return BaseEncoding.base64Url().decode(value);
    }

    public static byte[] checksum(byte[] value) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(value);
    }

    public byte[] getPK() {
        return serviceID.publicKey;
    };

    /* Handles: /serviceid */
    private void retrieveServiceID() throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        URI uri = UriBuilder
                .fromUri(nimble_rest_uri)
                .replacePath("/serviceid")
                .build();

        // Build HTTP request
        HttpGet request = new HttpGet(uri);

        // Execute HTTP request
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            Map<?, ?> json = Nimble.parseJSON(response.getEntity());
            serviceID = new NimbleServiceID((String) json.get("Identity"), (String) json.get("PublicKey"));
            logger.debug(serviceID);
        }
    }

    private URI getCounterURI(String handle) {
        return UriBuilder
                .fromUri(nimble_rest_uri)
                .path("/counters/" + handle)
                .build();
    }

    public Nimble(Configuration conf) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        nimble_rest_uri = URI.create(conf.get(NIMBLEURI_KEY, NIMBLEURI_DEFAULT));
        httpClient = HttpClients.custom()
                .disableRedirectHandling()
                .build();

        // Initialize identity of server
        retrieveServiceID();
    }

    public synchronized void close() throws IOException {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.error(e);
        }
    }

    private static Map<?,?> parseJSON(HttpEntity entity) throws IOException {
        // Ensure JSON content
        String contentType = entity.getContentType().getValue();
        if (!contentType.equalsIgnoreCase("application/json")) {
            throw new NimbleError("expected JSON Content-Type");
        }

        // Parse JSON
        String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        Map<?, ?> json = JsonSerialization.mapReader().readValue(responseBody);
        return json;
    }

    /**
     * @param handle    Handle for request
     * @param tag       Tag
     * @return          Signature (from TMCS)
     * @throws IOException
     */
    /* Handles: /counters/ */
    public NimbleOpNewCounter newCounter(byte[] handle, byte[] tag) throws IOException {
        String handle_str = Nimble.URLEncode(handle),
               tag_str    = Nimble.URLEncode(tag);
        URI uri = getCounterURI(handle_str);

        // Build HTTP request
        HttpPut request = new HttpPut(uri);
        byte[] body = String
                .format("{\"Tag\": \"%s\"}", tag_str)
                .getBytes();
        request.setEntity(new ByteArrayEntity(body));
        request.setHeader("Content-type", "application/json");

        // Execute HTTP request
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200: break;
                case 409: throw new NimbleError("Conflict Handle=" + handle_str);
                default: throw new NimbleError("Failed newCounter: " + response.getStatusLine());
            }

            Map<?, ?> json = Nimble.parseJSON(response.getEntity());
            logger.debug(String.format("NewCounter response: %s", json.toString()));
            return new NimbleOpNewCounter(serviceID, handle, tag, json);
        }
    }

    /**
     * Read the latest value from TMCS
     *
     * @param handle
     * @return
     * @throws IOException
     */
    public NimbleOpReadLatest readLatest(byte[] handle) throws IOException {
        byte[] nonce = Nimble.getNonce();
        String handle_str = Nimble.URLEncode(handle),
                nonce_str    = Nimble.URLEncode(nonce);
        URI uri = UriBuilder
                .fromUri(getCounterURI(handle_str))
                .queryParam("nonce", nonce_str)
                .build();

        // Build HTTP request
        HttpGet request = new HttpGet(uri);

        // Execute HTTP request
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200: break;
                default: throw new NimbleError("Failed readLatest: " + response.getStatusLine());
            }

            Map<?, ?> json = parseJSON(response.getEntity());
            logger.debug(String.format("ReadLatest response: %s", json.toString()));
            return new NimbleOpReadLatest(serviceID, handle, nonce, json);
        }
    }

    public NimbleOpIncrementCounter incrementCounter(byte[] handle, byte[] tag, int expected) throws IOException {
        String handle_str = Nimble.URLEncode(handle),
                tag_str   = Nimble.URLEncode(tag);
        URI uri = getCounterURI(handle_str);

        // Build HTTP request
        HttpPost request = new HttpPost(uri);
        byte[] body = String
                .format("{\"Tag\": \"%s\", \"ExpectedCounter\": %d}", tag_str, expected)
                .getBytes();
        request.setEntity(new ByteArrayEntity(body));
        request.setHeader("Content-type", "application/json");

        // Execute HTTP request
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200: break;
                default: throw new NimbleError("Failed readLatest: " + response.getStatusLine());
            }

            Map<?, ?> json = parseJSON(response.getEntity());
            logger.debug(String.format("IncrementCounter response: %s", json.toString()));
            return new NimbleOpIncrementCounter(serviceID, handle, tag, expected, json);
        }
    }

    public static File getNimbleInfo(Storage.StorageDirectory sd) {
        if (sd.getRoot() == null) {
            return null;
        }
        return new File(sd.getCurrentDir(), NIMBLE_INFO);
    }

    public static void saveNimbleInfo(Storage.StorageDirectory sd, Nimble n) throws IOException {
        File nimble_info = getNimbleInfo(sd);
        if (nimble_info == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("identity", Nimble.URLEncode(n.serviceID.identity));
        props.setProperty("publicKey", Nimble.URLEncode(n.serviceID.publicKey));
        // add local signing keys to "this", or "another file"? (actually stored in Azure Key Vault)
        Storage.writeProperties(nimble_info, props);
        logger.info(props);
    }

    public void saveFSImageInfo(Storage.StorageDirectory sd, File fsimage, byte[] digest) throws IOException {
        // Ensure handle exists
        try {
            NimbleConf.initialize();
            newCounter(NimbleConf.handle, "initialize".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // ignore NimbleError in case of conflict
        }

        // Prepare data
        Properties props = new Properties();
//        props.setProperty("sha256sum", Nimble.URLEncode(digest));
        props.setProperty("md5sum", Nimble.URLEncode(digest));
        props.setProperty("counter", String.valueOf(readLatest(NimbleConf.handle).counter));

        // Store data
        File nimble_info = new File(fsimage.getAbsolutePath()+NIMBLE_FSIMAGE_EXTENSION);
        Storage.writeProperties(nimble_info, props);
        logger.info(props);
    }

    /* For development and testing only */
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, InvalidKeySpecException, SignatureException, InvalidParameterSpecException, DecoderException {
        logger.setLevel(Level.DEBUG);
        logger.debug("Run Nimble workflow for testing");
        Configuration conf = new Configuration();

        try (Nimble n = new Nimble(conf)) {
            test_metadata(conf, n);
            test_workflow(n);
        }
    }

    /**
     * Nimble-related metadata
     */
    public static void test_metadata(Configuration conf, Nimble n) throws IOException {
        // Store handle in persistent file
        FSImage image = new FSImage(conf);
        NNStorage storage = image.getStorage();
        storage.format(); // creates dirs

        // Save TMCS ID to all storage directories
        for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext();) {
            Storage.StorageDirectory sd = it.next();
            logger.info(sd);
            saveNimbleInfo(sd, n);
        }
    }

    /**
     * Test TMCS Nimble API
     */
    public static void test_workflow(Nimble n) throws IOException {
        // Step 0: Sanity checks
//            test_verify_1(n);
//            test_verify_2(n);

        // Step 1: NewCounter Request
        byte[]   handle = getNonce();
        byte[]   tag    = "some-tag-value".getBytes();
        NimbleOp op;

        op = n.newCounter(handle, tag);
        logger.info("NewCounter (verify): " + op.verify());

        // Step 2: Read Latest w/ Nonce
        op = n.readLatest(handle);
        logger.info("ReadLatest (verify): " + op.verify());

        // Step 3: Increment Counter
        op = n.incrementCounter(handle, "tag_1".getBytes(), 1);
        logger.info("IncrementCounter " + "tag=tag_1 counter=1" + " (verify): " + op.verify());

        op = n.incrementCounter(handle, "tag_2".getBytes(), 2);
        logger.info("IncrementCounter " + "tag=tag_2 counter=2" + " (verify): " + op.verify());

        // Step 4: Read Latest w/ Nonce
        op = n.readLatest(handle);

        assert op.tag == "tag_2".getBytes();
        assert op.counter == 2;
        logger.info("Verify readLatest: " + op.verify());
    }

   /**
    * From output of light_client_rest
    */
    public static void test_verify_1(Nimble n) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        String d_str = "cix-tx9U14vwW-U50K-l9uRe17FWZGX3VqvvII4nIg0",
                s_str = "0Cku2NbVNZAC6OOQIHCxDFEnff7ManHLu1hlrDfurNmAXiFUsQ8ddIvM4i5-oG7JsyCbEstNUWouAKdD8x-f5A";
        byte[] d = URLDecode(d_str), s = URLDecode(s_str);
        boolean res = n.serviceID.verifySignature(s, d);
        logger.info("pk = " + NimbleOp.toNimbleStringRepr(n.getPK()));

        logger.info("digest = " + NimbleOp.toNimbleStringRepr(d));
        logger.info("signature = " + NimbleOp.toNimbleStringRepr(s));
        logger.info("digest = " + new String(d));
        logger.info("signature = " + new String(s));

        logger.info("Signature verification: " + res);
    }

    /**
     * From ledger/src/signature.rs: test_compressed_pk_and_raw_signature_encoding()
     */
    public static void test_verify_2(Nimble n) throws DecoderException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException, SignatureException, InvalidKeyException {
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

        NimbleServiceID service = new NimbleServiceID(new byte[]{}, pk_b);
        logger.info(service);
        boolean signature = service.verifySignature(sig_b, m_b);
        logger.info("Signature: " + signature);
    }
}
