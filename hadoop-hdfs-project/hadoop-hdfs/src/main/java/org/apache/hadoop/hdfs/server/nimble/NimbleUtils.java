package org.apache.hadoop.hdfs.server.nimble;

import org.apache.commons.lang3.RandomUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.NNStorage;
import org.apache.hadoop.thirdparty.com.google.common.io.BaseEncoding;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;

/* Store & load configuration */
public class NimbleUtils {
    static Logger logger = Logger.getLogger(NimbleUtils.class);
    public static class Conf {
        public static final String SERVICE_IDENTITY_KEY      = "fs.nimble.service.id";
        public static final String SERVICE_IDENTITY_DEFAULT  = "";
        public static final String SERVICE_PUBLIC_KEY_KEY    = "fs.nimble.service.publicKey";
        public static final String SERVICE_PUBLIC_KEY_DEFAULT= "";
        public static final String SERVICE_HANDLE_KEY        = "fs.nimble.service.handle";
        public static final String SERVICE_HANDLE_DEFAULT    = null;
        public static final String NIMBLE_LEDGER_URI_KEY     = "fs.nimbleURI";
        public static final String NIMBLE_LEDGER_URI_DEFAULT = "http://localhost:8082/";
        public static final String BATCH_SIZE_KEY            = "fs.nimble.batchSize";
        public static final long BATCH_SIZE__DEFAULT         = 2;
    }

    // URL of NimbleLedger's REST endpoint
    // Number of EditLog operations to batch
    public static final String NIMBLE_INFO              = "NIMBLE";
    public static final String NIMBLE_FSIMAGE_EXTENSION = ".nimble";
    public static final boolean READABLE_LOG_OPERATIONS = true;

    static {
        /* Enable debug logging for all Nimble classes, if debugging is enabled on this class */
        if (logger.isDebugEnabled()) {
            NimbleAPI.logger.setLevel(Level.DEBUG);
            TMCS.logger.setLevel(Level.DEBUG);
            TMCSEditLog.logger.setLevel(Level.DEBUG);
        }
    }
    public static boolean debug() {
        return logger.isDebugEnabled();
    }

    public static File getNimbleInfo(Storage.StorageDirectory sd) {
        if (sd.getRoot() == null) {
            return null;
        }
        return new File(sd.getCurrentDir(), NimbleUtils.NIMBLE_INFO);
    }

    /**
     * If Nimble metadata is stored on filesystem, then
     * return it after verifying identity of server.
     *
     * If no metatdata is stored, then retrieve it from
     * the server. A new *random* handle is generated.
     *
     * NOTE: New metadata is *not saved* to disk.
     */
    public static NimbleServiceID loadAndValidateNimbleInfo(NimbleAPI n) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        NimbleServiceID
                serverID = n.getServiceID(),
                savedID = NimbleUtils.loadNimbleInfo(n.conf);

        if (savedID == null || !savedID.valid()) { /* we don't have saved ID */
            serverID.handle = getNonce();
            NimbleUtils.saveNimbleInfo(n.conf, serverID);
            return serverID;
        }

        if (Arrays.equals(serverID.identity, savedID.identity) &&
                Arrays.equals(serverID.publicKey, savedID.publicKey))
            return savedID;

        throw new NimbleError(String.format("Server identity has changed: %s != %s",
                savedID, serverID));
    }

    /**
     * Load saved metadata about Nimble
     */
    public static NimbleServiceID loadNimbleInfo(Configuration conf) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        // Store handle in persistent file
        FSImage   image   = new FSImage(conf);
        NNStorage storage = image.getStorage();
        NimbleServiceID first = null, other;

        // Load ID from storage directories
        try {
            // Get ID from one directory
            for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext(); ) {
                Storage.StorageDirectory sd = it.next();
                if (first == null) {
                    first = loadNimbleInfo(conf, sd);
                    break;
                }
            }

            // Verify all other IDs are equal
            for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext(); ) {
                Storage.StorageDirectory sd = it.next();
                other = loadNimbleInfo(conf, sd);

                if (!first.equals(other)) {
                    logger.error(String.format("unexpected: %s != %s", first, other));
                    throw new NimbleError("Inconsistent Nimble metadata");
                }
            }

            return first;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Save metadata about Nimble
     */
    public static void saveNimbleInfo(Configuration conf, NimbleServiceID id) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        // Store handle in persistent file
        FSImage   image   = new FSImage(conf);
        NNStorage storage = image.getStorage();

        // Get ID from one directory
        for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext(); ) {
            Storage.StorageDirectory sd = it.next();
            saveNimbleInfo(sd, id);
        }
    }

    public static NimbleServiceID loadNimbleInfo(Configuration conf, Storage.StorageDirectory sd) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        if (sd == null || sd.getRoot() == null) {
            return null;
        }

        Properties props = Storage.readPropertiesFile(getNimbleInfo(sd));
        NimbleServiceID fsID = new NimbleServiceID(
                props.getProperty("identity", Conf.SERVICE_IDENTITY_DEFAULT),
                props.getProperty("publicKey", Conf.SERVICE_PUBLIC_KEY_DEFAULT),
                props.getProperty("handle", Conf.SERVICE_HANDLE_DEFAULT),
                props.getProperty("signPublicKey"),
                props.getProperty("signPrivateKey")
        );

        NimbleServiceID confID = new NimbleServiceID(
                conf.get(Conf.SERVICE_IDENTITY_KEY, Conf.SERVICE_IDENTITY_DEFAULT),
                conf.get(Conf.SERVICE_PUBLIC_KEY_KEY, Conf.SERVICE_PUBLIC_KEY_DEFAULT),
                conf.get(Conf.SERVICE_HANDLE_KEY, Conf.SERVICE_HANDLE_DEFAULT),
                // TODO: Load from Azure Key Vault
                props.getProperty("signPublicKey"),
                props.getProperty("signPrivateKey")
        );

        if (!fsID.equals(confID))
            logger.warn("Embedded Nimble identity differs from configuration! Defaulting to embedded identity.");
        else
            logger.info("Embedded Nimble identity matches the configuration. All OK.");
        return fsID;
    }

    public static void saveNimbleInfo(Storage.StorageDirectory sd, NimbleServiceID id) throws IOException {
        File nimble_info = getNimbleInfo(sd);
        if (nimble_info == null) {
            return;
        }

        try {
            if (!id.canSign()) {
                logger.info("generating signing keys for Nimble");
                id.generateSigningKeys();
            }
        } catch (Exception e) {
            logger.error("cannot generate signing keys: " + e);
            throw new NimbleError("cannot generate signing keys");
        }

        Properties props = new Properties();
        props.setProperty("identity", URLEncode(id.identity));
        props.setProperty("publicKey", URLEncode(id.publicKey));
        props.setProperty("handle", URLEncode(id.handle));
        // TODO: Store in Azure Key Vault
        props.setProperty("signPublicKey", URLEncode(id.getSignPublicKey()));
        props.setProperty("signPrivateKey", URLEncode(id.getSignPrivateKey()));
        Storage.writeProperties(nimble_info, props);
    }

    /**
     * Called from FSImageSaver to save "fsimage_###.nimble"
     */
    public static void saveFSImageInfo(File fsimage) throws IOException, NoSuchAlgorithmException {
        // Prepare data
        Properties props = new Properties();
        TMCS tmcs = TMCS.getInstance();
        byte[] digest = NimbleUtils.checksum(fsimage);
        byte[] tag = getTagForFSImage(digest, tmcs.expectedCounter());
        logger.info("Signed tag for FSImage: " + URLEncode(tag));

        props.setProperty("sha256sum-fsimage", URLEncode(digest));
        props.setProperty("counter", String.valueOf(tmcs.expectedCounter()));
        props.setProperty("tag", URLEncode(tag));
        // add status tag? (to simplify recovery logic during failures)

        // Store data
        File nimble_info = new File(fsimage.getParentFile(), fsimage.getName() + NIMBLE_FSIMAGE_EXTENSION);
        Storage.writeProperties(nimble_info, props);
        logger.info(props);
    }

    public static class NimbleFSImageInfo {
        public int counter;
        public byte[] digest;
        public byte [] tag;

        public NimbleFSImageInfo(int counter, byte[] digest, byte[] tag) {
            this.counter = counter;
            this.digest = digest;
            this.tag = tag;
        }
    }

    /**
     * Called from FSImageSaver to load "fsimage_###.nimble"
     */
    public static NimbleFSImageInfo getFSImageInfo(File fsimage) throws IOException {
        logger.info("getFSImageInfo for: "+fsimage);
        File nimble_info = new File(fsimage.getParentFile(), fsimage.getName() + NIMBLE_FSIMAGE_EXTENSION);
        Properties props = Storage.readPropertiesFile(nimble_info);
        byte[] digest_curr = NimbleUtils.checksum(fsimage);

        byte[] digest_saved = URLDecode(props.getProperty("sha256sum-fsimage", ""));
        int counter_saved = Integer.parseInt(props.getProperty("counter", "-2"));
        byte[] tag_saved = URLDecode(props.getProperty("tag", ""));

        // Sanity checks
        if (!Arrays.equals(digest_curr, digest_saved))
            throw new NimbleError("Saved & current checksums do not match");

        return new NimbleFSImageInfo(counter_saved, digest_saved, tag_saved);
    }

    public static boolean verifyFSImageInfo(File fsImage) throws IOException {
        // The following also verifies the signature on FSImage's tag
        NimbleFSImageInfo info = NimbleUtils.getFSImageInfo(fsImage);
        return verifyTagForFSImage(info.tag, info.digest, info.counter);
    }

    public static void renameFSImageInfo(File from, File to) throws IOException {
        File fromNimble = new File(from.getParentFile(), from.getName()+NIMBLE_FSIMAGE_EXTENSION);
        File toNimble = new File(to.getParentFile(), to.getName()+NIMBLE_FSIMAGE_EXTENSION);

        boolean flag = fromNimble.renameTo(toNimble);
        if (!flag) {
            String m = "failed to rename file: " + fromNimble + " to " + toNimble;
            throw new NimbleError(m);
        }
    }

    public static byte[] getTagForFSImage(byte[] digest, int counter) throws IOException {
        try {
            Signature tag = TMCS.getInstance().getSignature();
            tag.update(InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8));
            return tag.sign();
        } catch (SignatureException e) {
            throw new NimbleError("Cannot compute signature: " + e);
        }
    }

    public static boolean verifyTagForFSImage(byte[] tag, byte[] digest, int counter) throws IOException {
        try {
            Signature vTag = TMCS.getInstance().verifySignature();
            vTag.update(InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8));
            return vTag.verify(tag);
        } catch (SignatureException e) {
            throw new NimbleError("Cannot compute signature: " + e);
        }
    }

    public static byte[] getNonce() {
        return RandomUtils.nextBytes(16);
    }

    public static String URLEncode(byte[] value) {
        if (value == null)
            return "null";
        return BaseEncoding.base64Url().omitPadding().encode(value);
    }

    public static byte[] URLDecode(String value) {
        if (value.equals("null") || value.equals(""))
            return null;
        return BaseEncoding.base64Url().decode(value);
    }

    public static MessageDigest _checksum() throws NimbleError {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new NimbleError("cannot compute SHA256");
        }
    }

    public static byte[] checksum(byte[] value) throws NimbleError {
        MessageDigest md = _checksum();
        return md.digest(value);
    }

    public static byte[] checksum(File file) throws IOException {
        MessageDigest       md  = _checksum();
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

        byte[] buffer = new byte[8192];
        int count;
        while ((count = bis.read(buffer)) > 0) {
            md.update(buffer, 0, count);
        }

        return md.digest();
    }
}
