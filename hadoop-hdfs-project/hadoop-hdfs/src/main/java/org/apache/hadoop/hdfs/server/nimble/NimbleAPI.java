package org.apache.hadoop.hdfs.server.nimble;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.JsonSerialization;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.ws.rs.core.UriBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.Map;

public class NimbleAPI implements Closeable {
    static  Logger logger = Logger.getLogger(NimbleAPI.class);

    private URI                 nimble_rest_uri;
    private CloseableHttpClient httpClient;
    final public Configuration       conf;

    public NimbleAPI(Configuration conf) {
        this.conf = conf;
        this.nimble_rest_uri = URI.create(conf.get(NimbleUtils.NIMBLEURI_KEY, NimbleUtils.NIMBLEURI_DEFAULT));
        this.httpClient = HttpClients.custom()
                .disableRedirectHandling()
                .build();
    }

    public synchronized void close() throws IOException {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.error(e);
        }
    }

    private URI getCounterURI(String handle) {
        return UriBuilder
                .fromUri(nimble_rest_uri)
                .path("/counters/" + handle)
                .build();
    }

    private Map<?,?> parseJSON(HttpEntity entity) throws IOException {
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

    public boolean verifyServiceID(NimbleServiceID with) {
        try {
            NimbleServiceID remote = getServiceID();
            return Arrays.equals(remote.identity, with.identity) &&
                    Arrays.equals(remote.publicKey, with.publicKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * GET: /serviceid/
     * Request Body: empty
     * Response Body: { "Identity": ..., "PublicKey": ... }
     */
    public NimbleServiceID getServiceID() throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeySpecException, NoSuchProviderException {
        URI uri = UriBuilder
                .fromUri(nimble_rest_uri)
                .replacePath("/serviceid")
                .queryParam("pkformat", "compressed")
                .build();

        // Build HTTP request
        HttpGet request = new HttpGet(uri);

        // Execute HTTP request
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            Map<?, ?> json = parseJSON(response.getEntity());
            return new NimbleServiceID((String) json.get("Identity"), (String) json.get("PublicKey"), null);
        }
    }

    /**
     * PUT: /counters/[handle]
     * Request Body: { "Tag": "[tag]" }
     * Response Body: { "Signature": "..." }
     */
    public NimbleOpNewCounter newCounter(NimbleServiceID id, byte[] tag) throws IOException {
        String handle_str = NimbleUtils.URLEncode(id.handle),
                tag_str    = NimbleUtils.URLEncode(tag);
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

            Map<?, ?> json = parseJSON(response.getEntity());
            logger.debug(String.format("NewCounter response: %s", json.toString()));
            return new NimbleOpNewCounter(id, id.handle, tag, json);
        }
    }

    /**
     * GET: /counters/[handle]
     * Request Body: empty
     * Response Body: { "Counter": ..., "Tag": ..., "Signature": ... }
     */
    public NimbleOpReadLatest readLatest(NimbleServiceID id) throws IOException {
        byte[] nonce = NimbleUtils.getNonce();
        String handle_str = NimbleUtils.URLEncode(id.handle),
                nonce_str    = NimbleUtils.URLEncode(nonce);
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
            return new NimbleOpReadLatest(id, id.handle, nonce, json);
        }
    }

    /**
     * POST: /counters/[handle]
     * Request Body: { "Tag": "[tag]", "ExpectedCounter": [expected] }
     * Response Body: { "Signature": "..." }
     */
    public NimbleOpIncrementCounter incrementCounter(NimbleServiceID id, byte[] tag, int expected) throws IOException {
        String handle_str = NimbleUtils.URLEncode(id.handle),
                tag_str   = NimbleUtils.URLEncode(tag);
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
            return new NimbleOpIncrementCounter(id, id.handle, tag, expected, json);
        }
    }
}
