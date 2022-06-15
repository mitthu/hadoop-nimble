package org.apache.hadoop.hdfs.server.nimble;

import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOpCodes;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * We aggregate EditLog ops for fixed number, or till we encounter the special NimbleOp (or just flush?).
 * Then we increment TMCS. The Tag contains the PreviousTag with ops.
 *
 * To Verify: when applying the EditLogs, we keep doing the same.
 */
public class TMCSEditLog {
    static Logger logger = Logger.getLogger(TMCS.class);

    final public static int AGGREGATE_FREQUENCY = 2; // TODO: change to 100

    private NimbleUtils.NimbleFSImageInfo fsImage; // base image for all operations
    private boolean apply; // false means don't increment to TMCS (we're verifying)
    private int num, nextCounter;
    private byte[] tag, previousTag;
    private MessageDigest    md;
    private DataOutputStream out; // writes used to compute checksum

    public static class NullOutputStream extends OutputStream {
        public void write(int b) throws IOException {}
        public void write(byte[] var1, int var2, int var3) throws IOException {}
    }

    public TMCSEditLog(boolean apply, File fsImageFile) throws IOException {
        this(apply, NimbleUtils.getFSImageInfo(fsImageFile));
    }

    public TMCSEditLog(boolean apply, NimbleUtils.NimbleFSImageInfo fsImage) throws IOException {
        this.apply = apply;
        this.fsImage = fsImage;

        this.num = 0;
        this.nextCounter = fsImage.counter;
        this.tag = fsImage.tag;

        prepareNextBatch();
    }

    private void prepareNextBatch() throws IOException {
        previousTag = tag;
        nextCounter++;
        num = 0;

        // Build tag
        md = NimbleUtils._checksum();
        md.update(String.valueOf(nextCounter).getBytes(StandardCharsets.UTF_8));
        md.update(previousTag);
        out = new DataOutputStream(
                new DigestOutputStream(new NullOutputStream(), md)
        );
    }

    // Send data to EditLogs
    private void finalizeBatch() throws IOException {
        byte[] digest = md.digest();
        tag = digest; // TODO: Sign this!
        if (apply) {
            TMCS.getInstance().increment(tag);
            logger.info(TMCS.getInstance().latest());
        }

        // Prepare for next batch
        prepareNextBatch();
    }

    /**
     * Record an operation
     */
    public synchronized void add(FSEditLogOp op) throws IOException {
        out.write(op.opCode.getOpCode()); // OPCODE
        op.writeFields(out); // Fields
        num++;
        logger.info("record: " + op);
        if (num >= AGGREGATE_FREQUENCY) // OR a special op
            finalizeBatch();
    }

    public synchronized void verifyState() throws IOException {
        NimbleOpReadLatest latest = TMCS.getInstance().latest();
        // ensure tag, counter & signature are correct

        if (!Arrays.equals(previousTag, latest.tag))
            throw new NimbleError(String.format("Incorrect Tag: expecting=%s got=%s",
                    NimbleUtils.URLEncode(previousTag), NimbleUtils.URLEncode(latest.tag)));
        else if ((nextCounter-1) != latest.counter)
            throw new NimbleError(String.format("Incorrect Counter: expecting=%d got=%d",
                    (nextCounter-1), latest.counter));
        // TODO: Verify signature

        logger.info("State verified (calculated): " + this);
        logger.info("State verified (via TMCS): " + latest);
    }

    /**
     * When loading existing EditLogs from disk
     */
    public synchronized void loadMode() {
        logger.info("DO NOT APPLY mode: " + this);
        this.apply = false;
    }

    /**
     * When writing to EditLogs
     */
    public synchronized void liveMode() {
        logger.info("LIVE mode: " + this);
        this.apply = true;
    }

    @Override
    public String toString() {
        return "TMCSEditLog{" +
                "counter=" + (nextCounter-1) +
                ", tag=" + NimbleUtils.URLEncode(tag) +
                '}';
    }
}
