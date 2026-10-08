package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Direct HTTPS client for the unofficial NetEase WeAPI endpoints. */
public final class NetEaseApiClient {
    private static final String SEARCH_PATH = "/weapi/cloudsearch/get/web";
    private static final String LYRIC_PATH = "/weapi/song/lyric";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final AtomicLong LAST_QR_TIMESTAMP = new AtomicLong();
    private final Transport transport;

    public NetEaseApiClient() {
        this(new UrlConnectionTransport());
    }

    public NetEaseApiClient(Transport transport) {
        if (transport == null) throw new IllegalArgumentException("transport is required");
        this.transport = transport;
    }

    public LoginResult login(String account, String password) throws IOException {
        if (password == null) return LoginResult.failure("请输入手机号或邮箱及密码");
        return login(account, password.toCharArray());
    }

    public LoginResult login(String account, char[] password) throws IOException {
        String cleanAccount = account == null ? "" : account.trim();
        if (password == null) {
            return LoginResult.failure("请输入手机号或邮箱及密码");
        }
        if (cleanAccount.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            return LoginResult.failure("请输入手机号或邮箱及密码");
        }
        boolean phone = isPhoneAccount(cleanAccount);
        String passwordHash;
        String json;
        String path;
        try {
            passwordHash = md5(password);
        } catch (Exception exception) {
            Arrays.fill(password, '\0');
            return LoginResult.failure("无法准备登录请求");
        } finally {
            Arrays.fill(password, '\0');
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (phone) {
            String digits = cleanAccount.replaceAll("[^0-9]", "");
            if (digits.startsWith("86") && digits.length() > 11) digits = digits.substring(2);
            fields.put("phone", digits);
            fields.put("countrycode", "86");
            path = "/weapi/login/cellphone";
        } else {
            fields.put("username", cleanAccount);
            path = "/weapi/login";
        }
        fields.put("password", passwordHash);
        fields.put("rememberLogin", Boolean.TRUE);
        fields.put("csrf_token", "");
        json = jsonObject(fields);

        Response response = postEncrypted(path, json, "");
        return finishLoginResponse(response);
    }

    public ApiResult sendSmsCode(String phone) throws IOException {
        String normalizedPhone = normalizeMainlandPhone(phone);
        if (normalizedPhone.isEmpty()) return ApiResult.failure("请输入有效的中国大陆手机号");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ctcode", "86");
        fields.put("cellphone", normalizedPhone);
        Response response = postEncrypted("/weapi/sms/captcha/sent", jsonObject(fields), "");
        int code = responseCode(response.body);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            return ApiResult.failure(response.statusCode >= 500
                    ? "网易云验证码服务暂时不可用（HTTP " + response.statusCode + "），请稍后重试。"
                    : "网易云未能发送验证码（HTTP " + response.statusCode + "），请稍后重试。");
        }
        if (code == 200) return ApiResult.success("验证码已发送，请查看短信。为避免触发限制，请勿连续重复发送。");
        if (code == 8821) {
            return ApiResult.failure("网易云触发了行为验证（8821），暂时拒绝发送验证码。请先在官方网易云音乐 App 完成验证，稍后再试；不要连续重复请求。");
        }
        if (isVerificationChallenge(code)) {
            return ApiResult.failure("网易云要求先在官方网易云音乐 App 完成安全校验，之后再申请短信验证码（接口码 "
                    + code + "）。应用无法代替或绕过这项验证。");
        }
        if (code == 415 || code == 429 || code == 502 || code == 509) {
            return ApiResult.failure("网易云暂时限制了验证码请求（接口码 " + code + "），请在官方网易云音乐 App 确认账号状态后再稍后重试。");
        }
        return ApiResult.failure(responseMessage(response.body,
                "发送验证码失败（接口码 " + (code < 0 ? response.statusCode : code) + "）。"));
    }

    public LoginResult loginWithSmsCode(String phone, String verificationCode) throws IOException {
        String normalizedPhone = normalizeMainlandPhone(phone);
        String code = verificationCode == null ? "" : verificationCode.trim();
        if (normalizedPhone.isEmpty()) return LoginResult.failure("请输入有效的中国大陆手机号");
        if (!code.matches("[0-9]{4,8}")) return LoginResult.failure("请输入短信验证码");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("phone", normalizedPhone);
        fields.put("countrycode", "86");
        fields.put("captcha", code);
        fields.put("rememberLogin", Boolean.TRUE);
        fields.put("csrf_token", "");
        return finishLoginResponse(postEncrypted("/weapi/login/cellphone", jsonObject(fields), ""));
    }

    /** Creates a one-time key for the NetEase web QR login page. */
    public String createQrLoginKey() throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", 1);
        fields.put("csrf_token", "");
        Response response = postEncrypted("/weapi/login/qrcode/unikey?_=" + nextQrTimestamp(),
                jsonObject(fields), "");
        int code = responseCode(response.body);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IOException("网易云二维码服务暂时不可用（HTTP " + response.statusCode + "），请稍后重试。");
        }
        if (code != 200) {
            if (isQrChallenge(code)) throw new IOException(qrChallengeMessage(code));
            throw new IOException(responseMessage(response.body,
                    "网易云未能生成登录二维码（接口码 " + code + "），请稍后刷新。"));
        }
        JSONObject json = parseObject(response.body);
        String key = json.optString("unikey", "").trim();
        if (key.isEmpty()) throw new IOException("网易云没有返回二维码登录 key，请稍后刷新。");
        return key;
    }

    /** Polls QR login status. A session is exposed only for code 803 with MUSIC_U. */
    public QrLoginResult checkQrLogin(String key) throws IOException {
        String cleanKey = key == null ? "" : key.trim();
        if (cleanKey.isEmpty()) return QrLoginResult.error("二维码 key 无效，请刷新二维码。");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("key", cleanKey);
        fields.put("type", 1);
        fields.put("csrf_token", "");
        String path = "/weapi/login/qrcode/client/login?noCheckToken=1&_=" + nextQrTimestamp();
        Response response = postEncrypted(path, jsonObject(fields), "");
        int code = responseCode(response.body);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            if (response.statusCode == 403 || response.statusCode == 429) {
                return QrLoginResult.challenge(qrChallengeMessage(response.statusCode));
            }
            return QrLoginResult.error("网易云二维码查询失败（HTTP " + response.statusCode + "），请检查网络或刷新二维码。");
        }
        if (code == 801) return QrLoginResult.waitingScan();
        if (code == 802) return QrLoginResult.waitingConfirmation();
        if (code == 800) return QrLoginResult.expired();
        if (code == 803) {
            String cookie = cookieHeader(response.setCookies);
            if (hasNonEmptyCookie(cookie, "MUSIC_U")) return QrLoginResult.authorized(cookie);
            return QrLoginResult.error("网易云确认了二维码，但没有返回有效登录会话；请刷新二维码重试。");
        }
        if (isQrChallenge(code)) return QrLoginResult.challenge(qrChallengeMessage(code));
        return QrLoginResult.error(responseMessage(response.body,
                "网易云二维码登录暂不可用（接口码 " + code + "），请稍后重试。"));
    }

    private static long nextQrTimestamp() {
        return LAST_QR_TIMESTAMP.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
    }

    private static boolean isQrChallenge(int code) {
        return code == 8821 || code == -460 || code == -462 || code == 415
                || code == 502 || code == 509;
    }

    private static String qrChallengeMessage(int code) {
        return "网易云要求安全验证（接口码 " + code
                + "）。请先在官方网易云音乐 App 完成验证，再刷新二维码；应用无法代替或绕过这项验证。";
    }

    private static boolean hasNonEmptyCookie(String cookie, String name) {
        if (cookie == null || cookie.isEmpty()) return false;
        for (String pair : cookie.split(";")) {
            String cleanPair = pair.trim();
            int equals = cleanPair.indexOf('=');
            if (equals > 0 && name.equals(cleanPair.substring(0, equals).trim())
                    && !cleanPair.substring(equals + 1).trim().isEmpty()) return true;
        }
        return false;
    }

    private LoginResult finishLoginResponse(Response response) {
        int code = responseCode(response.body);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            if (response.statusCode >= 500) {
                return LoginResult.failure("网易云登录服务器暂时不可用（HTTP " + response.statusCode
                        + "），请稍后重试；仍可尝试 LRCLIB 备用歌词来源。");
            }
            if (response.statusCode == 403 || response.statusCode == 429) {
                return LoginResult.failure("网易云暂时拒绝了登录请求（HTTP " + response.statusCode
                        + "）。请在官方网易云音乐 App 确认账号状态并完成可能的安全验证后再试。");
            }
            return LoginResult.failure("网易云登录请求失败（HTTP " + response.statusCode + "），请稍后重试。");
        }
        if (code != 200) {
            if (code == 8821) {
                return LoginResult.failure("网易云触发了行为验证（8821），当前登录请求被风控。请先在官方网易云音乐 App 完成验证后稍后再试；如果刚才用的是密码方式，可改用短信验证码。应用无法绕过这项验证。");
            }
            if (isVerificationChallenge(code) || code == 415 || code == 509 || code == 502) {
                return LoginResult.failure("网易云返回了安全校验或登录限制提示（接口码 " + code
                        + "）。请先在官方网易云音乐 App 登录并完成验证，等待限制解除后再试；应用无法代替或绕过这项验证。");
            }
            return LoginResult.failure("网易云登录未通过（接口码 " + code + "），请确认账号信息后重试。");
        }
        String cookie = cookieHeader(response.setCookies);
        if (cookie.isEmpty()) return LoginResult.failure("登录响应没有有效会话，请稍后重试");
        return LoginResult.success(cookie);
    }

    private static boolean isVerificationChallenge(int code) {
        return code == -460 || code == -462;
    }

    private static String normalizeMainlandPhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() > 11) digits = digits.substring(2);
        return digits.matches("1[3-9][0-9]{9}") ? digits : "";
    }

    private static String responseMessage(String body, String fallback) {
        try {
            JSONObject json = new JSONObject(body == null ? "{}" : body);
            String message = json.optString("message", "").trim();
            if (message.isEmpty()) message = json.optString("msg", "").trim();
            return message.isEmpty() ? fallback : message;
        } catch (JSONException ignored) {
            return fallback;
        }
    }

    public List<Song> searchSongs(TrackInfo track, String cookie) throws IOException {
        if (track == null || track.getTitle().isEmpty()) return Collections.emptyList();
        JSONObject payload = new JSONObject();
        try {
            payload.put("s", (track.getTitle() + " " + track.getArtist()).trim());
            payload.put("type", 1);
            payload.put("offset", 0);
            payload.put("limit", 20);
            payload.put("total", true);
            payload.put("csrf_token", "");
        } catch (JSONException exception) {
            throw new IOException("Cannot prepare lyric search", exception);
        }
        Response response = postEncrypted(SEARCH_PATH, payload.toString(), cookie);
        JSONObject root = requireSuccess(response);
        JSONObject data = root.optJSONObject("result");
        JSONArray songs = data == null ? null : data.optJSONArray("songs");
        if (songs == null) return Collections.emptyList();
        List<Song> result = new ArrayList<>();
        for (int index = 0; index < songs.length(); index++) {
            JSONObject item = songs.optJSONObject(index);
            if (item == null) continue;
            long id = item.optLong("id", 0);
            String title = item.optString("name", "");
            String album = "";
            JSONObject albumObject = item.optJSONObject("al");
            if (albumObject != null) album = albumObject.optString("name", "");
            JSONArray artistList = item.optJSONArray("ar");
            if (artistList == null) artistList = item.optJSONArray("artists");
            List<String> names = new ArrayList<>();
            if (artistList != null) {
                for (int artistIndex = 0; artistIndex < artistList.length(); artistIndex++) {
                    JSONObject artist = artistList.optJSONObject(artistIndex);
                    if (artist != null && !artist.optString("name", "").trim().isEmpty()) {
                        names.add(artist.optString("name"));
                    }
                }
            }
            long duration = item.optLong("dt", item.optLong("duration", 0));
            if (id > 0 && !title.trim().isEmpty()) {
                result.add(new Song(id, title, join(names), album, duration));
            }
        }
        return result;
    }

    public String getLyric(long songId, String cookie) throws IOException {
        if (songId <= 0) return "";
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", songId);
            payload.put("lv", -1);
            payload.put("tv", -1);
            payload.put("rv", -1);
            payload.put("kv", -1);
            payload.put("yv", -1);
            payload.put("csrf_token", "");
        } catch (JSONException exception) {
            throw new IOException("Cannot prepare lyric request", exception);
        }
        JSONObject root = requireSuccess(postEncrypted(LYRIC_PATH, payload.toString(), cookie));
        JSONObject yrc = root.optJSONObject("yrc");
        if (yrc != null) {
            String wordLyrics = yrc.optString("lyric", "");
            if (!wordLyrics.trim().isEmpty() && !LrcParser.parse(wordLyrics).isEmpty()) return wordLyrics;
        }
        JSONObject lrc = root.optJSONObject("lrc");
        return lrc == null ? "" : lrc.optString("lyric", "");
    }

    static String loginPathForAccount(String account) {
        return isPhoneAccount(account) ? "/weapi/login/cellphone" : "/weapi/login";
    }

    private static int responseCode(String body) {
        if (body == null) return -1;
        Matcher matcher = Pattern.compile("\\\"code\\\"\\s*:\\s*(-?\\d+)").matcher(body);
        return matcher.find() ? parseInt(matcher.group(1)) : -1;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return -1; }
    }

    private static String jsonObject(Map<String, ?> fields) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, ?> field : fields.entrySet()) {
            if (json.length() > 1) json.append(',');
            json.append(jsonString(field.getKey())).append(':');
            Object value = field.getValue();
            if (value instanceof Boolean || value instanceof Number) json.append(value);
            else json.append(jsonString(value == null ? "" : value.toString()));
        }
        return json.append('}').toString();
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        String safe = value == null ? "" : value;
        for (int index = 0; index < safe.length(); index++) {
            char ch = safe.charAt(index);
            switch (ch) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (ch < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else escaped.append(ch);
            }
        }
        return escaped.append('"').toString();
    }

    private static boolean isPhoneAccount(String account) {
        if (account == null) return false;
        String value = account.trim();
        return value.matches("\\+?[0-9][0-9 -]{5,}");
    }

    private Response postEncrypted(String path, String json, String cookie) throws IOException {
        try {
            NetEaseWeApiCrypto.Payload encrypted = NetEaseWeApiCrypto.encrypt(json);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("params", encrypted.getParams());
            form.put("encSecKey", encrypted.getEncSecKey());
            return transport.post(path, form, cookie == null ? "" : cookie);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not encrypt the NetEase request", exception);
        }
    }

    private static JSONObject requireSuccess(Response response) throws IOException {
        JSONObject body = parseObject(response.body);
        int code = body.optInt("code", -1);
        if (response.statusCode < 200 || response.statusCode >= 300 || code != 200) {
            throw new IOException("NetEase lyric request failed (code "
                    + (code < 0 ? response.statusCode : code) + ")");
        }
        return body;
    }

    private static JSONObject parseObject(String body) throws IOException {
        try {
            return new JSONObject(body == null ? "{}" : body);
        } catch (JSONException exception) {
            throw new IOException("NetEase returned invalid data", exception);
        }
    }

    private static String cookieHeader(List<String> setCookies) {
        if (setCookies == null) return "";
        List<String> values = new ArrayList<>();
        for (String header : setCookies) {
            if (header == null) continue;
            String pair = header.split(";", 2)[0].trim();
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            String name = pair.substring(0, equals).trim();
            if (!name.matches("[A-Za-z0-9_]+")) continue;
            values.add(pair);
        }
        return join(values);
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append("; ");
            joined.append(value);
        }
        return joined.toString();
    }

    private static String md5(char[] value) throws Exception {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
        byte[] input = new byte[encoded.remaining()];
        encoded.get(input);
        byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("MD5").digest(input);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
        StringBuilder result = new StringBuilder(32);
        for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }

    public interface Transport {
        Response post(String path, Map<String, String> form, String cookie) throws IOException;
    }

    public static final class Response {
        public final int statusCode;
        public final String body;
        public final List<String> setCookies;

        public Response(int statusCode, String body, List<String> setCookies) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.setCookies = setCookies == null ? Collections.emptyList() : new ArrayList<>(setCookies);
        }
    }

    public static final class LoginResult {
        private final boolean success;
        private final String message;
        private final String cookie;

        private LoginResult(boolean success, String message, String cookie) {
            this.success = success;
            this.message = message;
            this.cookie = cookie;
        }

        static LoginResult success(String cookie) { return new LoginResult(true, "登录成功", cookie); }
        static LoginResult failure(String message) { return new LoginResult(false, message, ""); }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getCookie() { return cookie; }
    }

    public static final class ApiResult {
        private final boolean success;
        private final String message;

        private ApiResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        static ApiResult success(String message) { return new ApiResult(true, message); }
        static ApiResult failure(String message) { return new ApiResult(false, message); }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public enum QrLoginStatus {
        WAITING_SCAN, WAITING_CONFIRMATION, EXPIRED, AUTHORIZED, CHALLENGE, ERROR
    }

    public static final class QrLoginResult {
        private final QrLoginStatus status;
        private final String message;
        private final String cookie;

        private QrLoginResult(QrLoginStatus status, String message, String cookie) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.cookie = cookie == null ? "" : cookie;
        }

        static QrLoginResult waitingScan() {
            return new QrLoginResult(QrLoginStatus.WAITING_SCAN, "等待扫码", "");
        }
        static QrLoginResult waitingConfirmation() {
            return new QrLoginResult(QrLoginStatus.WAITING_CONFIRMATION,
                    "已扫码，请在网易云音乐 App 确认登录", "");
        }
        static QrLoginResult expired() {
            return new QrLoginResult(QrLoginStatus.EXPIRED, "二维码已过期，请刷新二维码", "");
        }
        static QrLoginResult authorized(String cookie) {
            return new QrLoginResult(QrLoginStatus.AUTHORIZED, "网易云账号登录成功", cookie);
        }
        static QrLoginResult challenge(String message) {
            return new QrLoginResult(QrLoginStatus.CHALLENGE, message, "");
        }
        static QrLoginResult error(String message) {
            return new QrLoginResult(QrLoginStatus.ERROR, message, "");
        }

        public QrLoginStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public String getCookie() { return cookie; }
    }

    public static final class Song {
        private final long id;
        private final String title;
        private final String artist;
        private final String album;
        private final long durationMs;

        Song(long id, String title, String artist, String album, long durationMs) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.album = album == null ? "" : album;
            this.durationMs = Math.max(0, durationMs);
        }
        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public long getDurationMs() { return durationMs; }
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public Response post(String path, Map<String, String> form, String cookie) throws IOException {
            if (path == null || !path.startsWith("/weapi/")) throw new IOException("Unsupported NetEase endpoint");
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://music.163.com" + path).openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                connection.setRequestProperty("Referer", "https://music.163.com/");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                String body = encodeForm(form);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String responseBody = input == null ? "" : readLimited(input);
                List<String> setCookies = new ArrayList<>();
                Map<String, List<String>> headers = connection.getHeaderFields();
                if (headers != null) {
                    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                        if (header.getKey() != null && "set-cookie".equalsIgnoreCase(header.getKey())
                                && header.getValue() != null) setCookies.addAll(header.getValue());
                    }
                }
                return new Response(status, responseBody, setCookies);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private static String encodeForm(Map<String, String> form) throws IOException {
            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> field : form.entrySet()) {
                if (body.length() > 0) body.append('&');
                body.append(URLEncoder.encode(field.getKey(), "UTF-8"));
                body.append('=');
                body.append(URLEncoder.encode(field.getValue(), "UTF-8"));
            }
            return body.toString();
        }

        private static String readLimited(InputStream input) throws IOException {
            try (InputStream source = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = source.read(chunk)) != -1) {
                    if (buffer.size() + count > MAX_RESPONSE_BYTES) throw new IOException("NetEase response too large");
                    buffer.write(chunk, 0, count);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
