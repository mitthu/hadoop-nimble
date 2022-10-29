package org.apache.hadoop.hdfs.server.nimble;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp;
import org.apache.hadoop.hdfs.server.namenode.NameNodeLayoutVersion;
import org.apache.log4j.Logger;

import java.io.*;
import java.security.Signature;
import java.security.SignatureException;

import static org.apache.hadoop.hdfs.server.namenode.FSEditLogOpCodes.OP_NIMBLE_FLUSH;

/**
 * We aggregate EditLog ops for fixed number, or till we encounter the special NimbleOp (or just flush?).
 * Then we increment TMCS. The Tag contains the PreviousTag with ops.
 *
 * To Verify: when applying the EditLogs, we keep doing the same.
 */
public class TMCSEditLog {
    static Logger logger = Logger.getLogger(TMCS.class);

    private Configuration conf;
    private long aggregateFrequency;
    private NimbleUtils.NimbleFSImageInfo fsImage; // base image for all operations
    private boolean apply; // false means don't increment to TMCS (we're verifying)
    private int num, nextCounter;
    private SignatureOutputStream tag, previousTag;
    private DataOutputStream out; // wrapper for tag
    private TMCS tmcs;

    public static class SignatureOutputStream extends OutputStream {
        protected Signature s, v;
        protected boolean doVerify;

        public SignatureOutputStream(Signature sign, Signature verify) {
            this.s = sign;
            this.v = verify;
            this.doVerify = false;
        }

        public void setVerify(boolean b) {
            this.doVerify = b;
        }

        public boolean verify(byte[] signature) throws IOException {
            try {
                return v.verify(signature);
            } catch (SignatureException e) { throw new NimbleError(e); }
        }

        public byte[] sign() throws IOException {
            try {
                return s.sign();
            } catch (SignatureException e) { throw new NimbleError(e); }
        }

        public void write(int b) throws IOException {
            try{
                s.update((byte) b);
                if (doVerify)
                    v.update((byte) b);
            } catch (SignatureException e) { throw new NimbleError(e); }
        }

        public void write(byte[] var1, int var2, int var3) throws IOException {
            try{
                s.update(var1, var2, var3);
                if (doVerify)
                    v.update(var1, var2, var3);
            } catch (SignatureException e) { throw new NimbleError(e); }
        }
    }

    public TMCSEditLog(Configuration conf, boolean apply, File fsImageFile) throws IOException {
        this(conf, apply, NimbleUtils.getFSImageInfo(fsImageFile));
    }

    public TMCSEditLog(Configuration conf, boolean apply, NimbleUtils.NimbleFSImageInfo fsImage) throws IOException {
        this.conf = conf;
        this.apply = apply;
        this.fsImage = fsImage;
        this.aggregateFrequency = conf.getLong(NimbleUtils.Conf.BATCH_SIZE_KEY, NimbleUtils.Conf.BATCH_SIZE__DEFAULT);

        this.num = 0;
        this.nextCounter = fsImage.counter;
        this.previousTag = null;
        this.tmcs = TMCS.getInstance();
        this.tag = new SignatureOutputStream(tmcs.getSignature(), tmcs.verifySignature());
        this.out = new DataOutputStream(this.tag);

        prepareNextBatch();
    }

    private void prepareNextBatch() throws IOException {
        nextCounter++;
        num = 0;

        // Build tag
        tag = new SignatureOutputStream(tmcs.getSignature(), tmcs.verifySignature());
    }

    // Send data to EditLogs
    private void finalizeBatch() throws IOException {
        // Write counter after ops
        out.writeInt(nextCounter);

        // Update TMCS
        this.previousTag = tag;
        if (apply) {
            tmcs.increment(tag.sign());
        }

        // Prepare for next batch
        prepareNextBatch();
    }

    /**
     * Record an operation
     */
    public synchronized void add(FSEditLogOp op) throws IOException {
        try {
            out.write(op.opCode.getOpCode()); // OPCODE
            op.writeFields(out, NameNodeLayoutVersion.CURRENT_LAYOUT_VERSION); // Fields
            num++;
            logger.debug(String.format("record: opcode=%X %s", op.opCode.getOpCode(), op));

            boolean flushOp = op.opCode.getOpCode() == OP_NIMBLE_FLUSH.getOpCode();
            if (num >= aggregateFrequency || flushOp) {
                if (flushOp)
                    logger.debug("NimbleFlushOp executed!");
                finalizeBatch();
            }
        } catch (IOException e) {
            logger.error(e); // Else some errors go unnoticed
            throw e;
        }
    }

    public synchronized void flush() throws IOException {
        if (num > 0) {
            logger.info("flush " + num + " edit log operations");
            finalizeBatch();
        }
    }

    /**
     * Record the tag of new FSImage
     *
     * Ensure to flush() pending operations before this.
     */
    public synchronized void recordImage(byte[] tag) throws IOException {
        logger.debug("record image creation");
        if (num > 0)
            throw new NimbleError(num + " edit log operations are still not flushed");

        // Record new FSImage creation. Expects a signed tag.
        tmcs.increment(tag);

        // Update bookkeeping
        nextCounter++;
    }

    /**
     * Should be called before invoking liveMode().
     */
    public synchronized void verifyState() throws IOException {
        NimbleOpReadLatest latest = tmcs.latest();

        // Sanity checks
        if ((nextCounter-1) != latest.counter)
            throw new NimbleError(String.format("Incorrect Counter: expecting=%d got=%d", (nextCounter-1), latest.counter));

        if (num > 0) {
            logger.warn("the last " + num + " ops will not be verified. This is likely due to unclean shutdown.");
        }

        if (previousTag == null) {
            logger.warn("No edit log ops to verify");
            return;
        }

        // Verify signature. The current tag is empty if all ops were committed before shutdown.
        if(!previousTag.verify(latest.tag))
            throw new NimbleError("Cannot verify signature on tag");

        logger.debug("State verified: " + latest);
    }

    /**
     * When loading existing EditLogs from disk
     */
    public synchronized void loadMode() throws IOError {
        logger.info("DO NOT APPLY mode: " + this);
        this.apply = false;
        this.tag.setVerify(true);
    }

    /**
     * When writing to EditLogs
     */
    public synchronized void liveMode() throws NimbleError {
        logger.info("LIVE mode: " + this);
        this.apply = true;
        this.tag.setVerify(false);
    }

    @Override
    public String toString() {
        byte[] tagArr;
        try {
            tagArr = tag.sign();
        } catch (IOException e) {
            tagArr = null;
        }
        return "TMCSEditLog{" +
                "counter=" + (nextCounter-1) +
                ", tag=" + tagArr +
                '}';
    }
}
