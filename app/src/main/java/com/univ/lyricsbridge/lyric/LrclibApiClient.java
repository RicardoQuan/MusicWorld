package com.univ.lyricsbridge.lyric;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Small HTTPS client for LRCLIB's public search API. */
public final class LrclibApiClient {
    private static final String BASE_URL = "https://lrclib.net";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final Transport transport;

    public LrclibApiClient() {
        this(new UrlConnectionTransport());
    }

    public LrclibApiClient(Transport transport) {
        if (transport == null) throw new IllegalArgumentException("transport is required");
        this.transport = transport;
    }

    public Response get(String path) throws IOException {
        if (path == null || !path.startsWith("/api/")) {
            throw new IOException("Unsupported LRCLIB endpoint");
        }
        Response response = transport.get(path);
        if (response == null) throw new IOException("LRCLIB returned no response");
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IOException("LRCLIB request failed (HTTP " + response.statusCode + ")");
        }
        try {
            Object json = new JSONTokener(response.body).nextValue();
            if (!(json instanceof JSONObject) && !(json instanceof JSONArray)) {
                throw new JSONException("Expected a JSON object or array");
            }
        } catch (JSONException exception) {
            throw new IOException("LRCLIB returned invalid JSON", exception);
        }
        return response;
    }

    public interface Transport {
        Response get(String path) throws IOException;
    }

    public static final class Response {
        public final int statusCode;
        public final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public Response get(String path) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "UNI-V-Lyrics-Bridge/1.0 (Android)");
                int status = connection.getResponseCode();
                InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String body = input == null ? "" : readLimited(input);
                return new Response(status, body);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private static String readLimited(InputStream input) throws IOException {
            try (InputStream source = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = source.read(chunk)) != -1) {
                    if (buffer.size() + count > MAX_RESPONSE_BYTES) {
                        throw new IOException("LRCLIB response too large");
                    }
                    buffer.write(chunk, 0, count);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
