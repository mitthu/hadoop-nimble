package org.apache.hadoop.hdfs.server.nimble;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;

public class TMCS implements Closeable {
    static Logger logger = Logger.getLogger(TMCS.class);

    private static TMCS instance;
    private NimbleServiceID id;
    private NimbleAPI api;
    private int counter = -1;

    private TMCS() throws NimbleError {
        api = new NimbleAPI(new Configuration());
        try {
            id = NimbleUtils.loadAndValidateNimbleInfo(api);
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

    public synchronized static TMCS getInstance() throws NimbleError {
        if (instance == null) {
            instance = new TMCS();
        }
        return instance;
    }

    @Override
    public synchronized void close() throws IOException {
        api.close();
        api = null; // fail just in case
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

    public synchronized void initialize(byte[] tag) throws IOException {
        NimbleOp op = api.newCounter(id, tag);
        counter = 0;
        op.verify(); // TODO: throw exception on failure
    }

    public synchronized void increment(byte[] tag) throws IOException {
        if (counter == -1)
            throw new NimbleError("not initialized");

        NimbleOp op = api.incrementCounter(id, tag, counter+1);
        counter++;
        op.verify(); // TODO: throw exception on failure
    }

    private NimbleOpReadLatest _latest() throws IOException {
        NimbleOpReadLatest op = api.readLatest(id);
        op.verify(); // TODO: throw exception on failure
        return op;
    }

    public synchronized NimbleOpReadLatest latest() throws IOException {
        if (counter == -1)
            throw new NimbleError("not initialized");

        NimbleOpReadLatest op = _latest();
        assert counter == op.counter;
        return op;
    }
}
