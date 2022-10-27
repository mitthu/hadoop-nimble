package org.apache.hadoop.hdfs.server.nimble;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.Signature;

public class TMCS implements Closeable {
    static Logger logger = Logger.getLogger(TMCS.class);

    private static TMCS instance;
    private NimbleServiceID id;
    private NimbleAPI api;
    private int counter = -1;

    private void _TMCS() throws NimbleError {
        api = new NimbleAPI(new Configuration());
        try {
            id = NimbleUtils.loadAndValidateNimbleInfo(api);
            logger.info("Loaded Nimble info: " + id);
        } catch (Exception e) {
            logger.error("cannot load/create nimble metadata");
            e.printStackTrace();
            throw new NimbleError("cannot load/create nimble metadata");
        }

        assert id.handle != null;

        // try updating the counter (if exists)
        try {
            NimbleOpReadLatest op = this._latest();
            counter = op.counter;
        } catch (NimbleError e) {
            logger.debug("assuming counter is not created " + e.getMessage());
        } catch (IOException e) {
            logger.warn(e);
        }
    }

    private TMCS() { }

    /**
     * Only to be used while formatting
     */
    private TMCS(Configuration conf) {
        if (conf == null)
            conf = new Configuration();
        this.api = new NimbleAPI(conf);
    }

    @Override
    public synchronized void close() throws IOException {
        api.close();
        api = null; // fail just in case
    }

    private static TMCS _getInstance() throws NimbleError {
        if (instance == null) {
            instance = new TMCS();
            instance._TMCS(); // default constructor
        }
        return instance;
    }
    public synchronized static TMCS getInstance() throws NimbleError {
        return _getInstance();
    }

    /**
     * Refresh service identity and create new ledger handle.
     */
    public synchronized static void format(Configuration conf) throws IOException {
        if (instance == null) {
            instance = new TMCS(conf);
        }

        try {
            instance.id = instance.api.getServiceID();
            instance.id.handle = NimbleUtils.getNonce();
            instance.id.generateSigningKeys();
            // newCounter whose tag=[hostname]. We don't sign it because it is not used.
            instance._initialize(InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8));
            logger.info("Formatted TMCS: " + instance.id);
        } catch (Exception e) {
            e.printStackTrace();
            throw new NimbleError(e.getMessage());
        }
    }

    public synchronized void save(Storage.StorageDirectory sd) throws IOException {
        NimbleUtils.saveNimbleInfo(sd, id);
    }

    // "counter" may be -1 due to temporary connection failure.
    // Call reReadCounter() to fix this.
    public synchronized boolean isInitializedFromCache() {
        if (counter == -1)
            return false;
        return true;
    }

    public synchronized void reReadCounter() throws IOException {
        NimbleOpReadLatest op = _latest();
        counter = op.counter;
    }

    public void _initialize(byte[] tag) throws IOException {
        NimbleOp op = api.newCounter(id, tag);
        counter = 0;
        if (!op.verify())
            throw new NimbleError("Verification failed for NewCounter");
        else
            logger.info("Verified NewCounter");
    }

    public synchronized void initialize(byte[] tag) throws IOException {
        _initialize(tag);
    }

    public Signature getSignature() throws NimbleError {
        return id.getSignature();
    }

    public Signature verifySignature() throws NimbleError {
        return id.verifySignature();
    }

    public synchronized void increment(byte[] tag) throws IOException {
        if (counter == -1)
            throw new NimbleError("not initialized");

        // TODO: Sign tag
        NimbleOp op = api.incrementCounter(id, tag, counter+1);
        counter++;
        if (!op.verify())
            throw new NimbleError("Verification failed for IncrementCounter");
        else
            logger.info("Verified IncrementCounter");
        logger.info(String.format("increment: newCounter=%d tag=%s",
                counter, NimbleUtils.URLEncode(tag)));
    }

    private NimbleOpReadLatest _latest() throws IOException {
        NimbleOpReadLatest op = api.readLatest(id);
        if (!op.verify())
            throw new NimbleError("Verification failed for ReadCounter");
        else
            logger.info("Verified ReadCounter");
        return op;
    }

    public synchronized NimbleOpReadLatest latest() throws IOException {
        if (counter == -1)
            throw new NimbleError("not initialized");

        NimbleOpReadLatest op = _latest();
        assert counter == op.counter;
        return op;
    }

    public synchronized int expectedCounter() throws IOException {
        if (counter == -1)
            throw new NimbleError("not initialized");
        return counter+1;
    }
}
