package org.apache.hadoop.hdfs.server.nimble;

import org.apache.commons.lang3.RandomUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.NNStorage;
import org.apache.hadoop.thirdparty.com.google.common.io.BaseEncoding;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;

/* Store & load configuration */
public class NimbleUtils {
    static Logger logger = Logger.getLogger(NimbleUtils.class);

    public static final String NIMBLEURI_KEY            = "fs.nimbleURI";
    public static final String NIMBLEURI_DEFAULT        = "http://localhost:8082/";
    public static final String NIMBLE_INFO              = "NIMBLE";
    public static final String NIMBLE_FSIMAGE_EXTENSION = ".nimble";
    public static final boolean READABLE_LOG_OPERATIONS = true;

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
                    first = loadNimbleInfo(sd);
                    break;
                }
            }

            // Verify all other IDs are equal
            for (Iterator<Storage.StorageDirectory> it = storage.dirIterator(); it.hasNext(); ) {
                Storage.StorageDirectory sd = it.next();
                other = loadNimbleInfo(sd);

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

    public static NimbleServiceID loadNimbleInfo(Storage.StorageDirectory sd) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        if (sd == null || sd.getRoot() == null) {
            return null;
        }

        Properties props = Storage.readPropertiesFile(getNimbleInfo(sd));
        return new NimbleServiceID(
                props.getProperty("identity", ""),
                props.getProperty("publicKey", ""),
                props.getProperty("handle")
        );
    }

    public static void saveNimbleInfo(Storage.StorageDirectory sd, NimbleServiceID id) throws IOException {
        File nimble_info = getNimbleInfo(sd);
        if (nimble_info == null) {
            return;
        }

        Properties props = new Properties();
        props.setProperty("identity", URLEncode(id.identity));
        props.setProperty("publicKey", URLEncode(id.publicKey));
        props.setProperty("handle", URLEncode(id.handle));
        // add local signing keys to "this", or "another file"? (actually stored in Azure Key Vault)
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

        // TODO: Verify signature
        byte[] tag_computed = getTagForFSImage(digest_curr, counter_saved);
        if (!Arrays.equals(tag_saved, tag_computed))
            throw new NimbleError("Saved & current tags do not match");

        return new NimbleFSImageInfo(counter_saved, digest_saved, tag_saved);
    }

    public static void verifyFSImageInfo(File fsImage) throws IOException {
        NimbleUtils.NimbleFSImageInfo info = NimbleUtils.getFSImageInfo(fsImage);
        byte[] digest = checksum(fsImage);

        if (!Arrays.equals(info.digest, digest))
            throw new NimbleError("Image file " + fsImage +
                    " is corrupt with SHA256 checksum of " + URLEncode(digest) +
                    " but expecting " + URLEncode(info.digest));

        // TODO: Verify signature
        logger.info("FSImage verified!");
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
        MessageDigest md = _checksum();
        md.update(digest);
        md.update(String.valueOf(counter).getBytes(StandardCharsets.UTF_8));
        // TODO: Sign it! tag=sk<sha256sum|counter>
        return md.digest();
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

    protected static MessageDigest _checksum() throws NimbleError {
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
