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
class NimbleUtils {
    static Logger logger = Logger.getLogger(NimbleUtils.class);

    public static final String NIMBLEURI_KEY            = "fs.nimbleURI";
    public static final String NIMBLEURI_DEFAULT        = "http://[::1]:8082/";
    public static final String NIMBLE_INFO              = "NIMBLE";
    public static final String NIMBLE_FSIMAGE_EXTENSION = ".nimble";
    public static final boolean READABLE_LOG_OPERATIONS = true;

    public static File getNimbleInfo(Storage.StorageDirectory sd) {
        if (sd.getRoot() == null) {
            return null;
        }
        return new File(sd.getCurrentDir(), NimbleUtils.NIMBLE_INFO);
    }

    public static NimbleServiceID loadAndValidateNimbleInfo(NimbleAPI n) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        NimbleServiceID
                serverID = n.getServiceID(),
                savedID = NimbleUtils.loadNimbleInfo(n.conf);

        if (!savedID.valid()) { /* we don't have saved ID */
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

    public void saveFSImageInfo(File fsimage) throws IOException, NoSuchAlgorithmException {
        // Ensure handle exists -- goes elsewhere (when formatting filesystem)
//        try {
//            NimbleConf.initialize();
//            NimbleAPI.newCounter(NimbleConf.handle, "initialize".getBytes(StandardCharsets.UTF_8));
//        } catch (Exception e) {
//            // ignore NimbleError in case of conflict
//        }

        // Prepare data
        Properties props = new Properties();
        byte[] digest = NimbleUtils.checksum(fsimage);
        props.setProperty("sha256sum", URLEncode(digest));
        props.setProperty("counter", "TODO: insert expected value");
        props.setProperty("tag", "TODO: sk<sha256sum|counter>");
        // add status tag? (to simplify recovery logic during failures)

        // Store data
        File nimble_info = new File(fsimage.getParentFile(), fsimage.getName() + NIMBLE_FSIMAGE_EXTENSION);
        Storage.writeProperties(nimble_info, props);
        logger.info(props);
    }


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

    public static byte[] checksum(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest       md  = MessageDigest.getInstance("SHA-256");
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

        byte[] buffer = new byte[8192];
        int count;
        while ((count = bis.read(buffer)) > 0) {
            md.update(buffer, 0, count);
        }

        return md.digest();
    }
}
