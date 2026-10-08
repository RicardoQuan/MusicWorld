package com.univ.lyricsbridge.lyric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class NetEaseApiClientTest {
    @Test
    public void phoneAndEmailSelectSupportedLoginRoutesAndOnlySendEncryptedFormFields() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        NetEaseApiClient client = new NetEaseApiClient(transport);

        NetEaseApiClient.LoginResult phone = client.login("13800138000", "secret-phone-pass");
        assertTrue(phone.getMessage(), phone.isSuccess());
        assertEquals("/weapi/login/cellphone", transport.paths.get(0));
        assertEquals(2, transport.forms.get(0).size());
        assertTrue(transport.forms.get(0).containsKey("params"));
        assertTrue(transport.forms.get(0).containsKey("encSecKey"));
        assertFalse(transport.forms.get(0).toString().contains("secret-phone-pass"));

        NetEaseApiClient.LoginResult email = client.login("driver@example.com", "secret-email-pass");
        assertTrue(email.isSuccess());
        assertEquals("/weapi/login", transport.paths.get(1));
        assertEquals(2, transport.forms.get(1).size());
        assertFalse(transport.forms.get(1).toString().contains("secret-email-pass"));
        assertNotNull(email.getCookie());
    }

    @Test
    public void failedLoginNeverReturnsSessionCookiesEvenIfResponseContainsOne() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200, "{\"code\":502}",
                        Collections.singletonList("MUSIC_U=must-not-be-used; Path=/")));

        NetEaseApiClient.LoginResult result = client.login("13800138000", "wrong-password");

        assertFalse(result.isSuccess());
        assertEquals("", result.getCookie());
    }

    @Test
    public void httpGatewayFailureIsNotReportedAsAnAccountSecurityChallenge() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(502, "Bad Gateway", Collections.emptyList()));

        NetEaseApiClient.LoginResult result = client.login("13800138000", "password");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("服务器暂时不可用"));
        assertTrue(result.getMessage().contains("502"));
        assertFalse(result.getMessage().contains("安全验证"));
    }

    @Test
    public void netEaseSecurityResponseExplainsThatOfficialVerificationIsRequired() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200, "{\"code\":502}", Collections.emptyList()));

        NetEaseApiClient.LoginResult result = client.login("13800138000", "password");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("安全校验"));
        assertTrue(result.getMessage().contains("官方网易云音乐 App"));
        assertTrue(result.getMessage().contains("502"));
    }

    @Test
    public void verificationCodeChallengeCodeIsExplainedInsteadOfGenericLoginFailure() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200,
                        "{\"code\":-462,\"message\":\"需要在网易云音乐 App 中验证\"}",
                        Collections.emptyList()));

        NetEaseApiClient.LoginResult result = client.login("13800138000", "password");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("安全校验"));
        assertTrue(result.getMessage().contains("官方网易云音乐 App"));
        assertTrue(result.getMessage().contains("-462"));
    }

    @Test
    public void behaviorVerificationCodeSuggestsSmsInsteadOfRetryingPassword() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200,
                        "{\"code\":8821,\"message\":\"行为验证\"}", Collections.emptyList()));

        NetEaseApiClient.LoginResult result = client.login("13800138000", "password");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("8821"));
        assertTrue(result.getMessage().contains("行为验证"));
        assertTrue(result.getMessage().contains("短信验证码"));
    }

    @Test
    public void sendSmsCodeUsesNetEasePhoneEndpointAndEncryptedFields() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        NetEaseApiClient client = new NetEaseApiClient(transport);

        NetEaseApiClient.ApiResult result = client.sendSmsCode("13800138000");

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("/weapi/sms/captcha/sent", transport.paths.get(0));
        assertEquals(2, transport.forms.get(0).size());
        assertTrue(transport.forms.get(0).containsKey("params"));
        assertTrue(transport.forms.get(0).containsKey("encSecKey"));
        assertFalse(transport.forms.get(0).toString().contains("13800138000"));
    }

    @Test
    public void smsCodeBehaviorVerificationExplainsOfficialAppStep() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200,
                        "{\"code\":8821,\"message\":\"行为验证\"}", Collections.emptyList()));

        NetEaseApiClient.ApiResult result = client.sendSmsCode("13800138000");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("8821"));
        assertTrue(result.getMessage().contains("官方网易云音乐 App"));
        assertTrue(result.getMessage().contains("不要连续重复请求"));
    }

    @Test
    public void smsCodeSecurityChallengeAsksForOfficialAppVerification() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200,
                        "{\"code\":-462,\"message\":\"需要在网易云音乐 App 中验证\"}",
                        Collections.emptyList()));

        NetEaseApiClient.ApiResult result = client.sendSmsCode("13800138000");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("安全校验"));
        assertTrue(result.getMessage().contains("官方网易云音乐 App"));
        assertTrue(result.getMessage().contains("-462"));
    }

    @Test
    public void smsCodeLoginReturnsSessionWithoutSendingPassword() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        NetEaseApiClient client = new NetEaseApiClient(transport);

        NetEaseApiClient.LoginResult result = client.loginWithSmsCode("13800138000", "123456");

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("/weapi/login/cellphone", transport.paths.get(0));
        assertEquals(2, transport.forms.get(0).size());
        assertFalse(transport.forms.get(0).toString().contains("123456"));
        assertTrue(result.getCookie().contains("MUSIC_U=session-cookie"));
    }

    @Test
    public void lyricLookupPrefersNetEaseWordTimedYrcAndRequestsIt() throws Exception {
        final boolean[] requested = {false};
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) -> {
            assertEquals("/weapi/song/lyric", path);
            requested[0] = true;
            return new NetEaseApiClient.Response(200,
                    "{\"code\":200,\"lrc\":{\"lyric\":\"[00:00.00]line\"},"
                            + "\"yrc\":{\"lyric\":\"[0,500](0,500,0)word\"}}",
                    Collections.emptyList());
        });

        assertEquals("[0,500](0,500,0)word", client.getLyric(42, "MUSIC_U=session"));
        assertTrue(requested[0]);
    }

    @Test
    public void malformedYrcUsesNetEaseLrcFromSameResponse() throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200,
                        "{\"code\":200,\"yrc\":{\"lyric\":\"malformed\"},"
                                + "\"lrc\":{\"lyric\":\"[00:01.00]valid\"}}",
                        Collections.emptyList()));

        assertEquals("[00:01.00]valid", client.getLyric(42, "MUSIC_U=session"));
    }

    @Test
    public void qrLoginCreatesUniqueKeyRequestsAndUsesOnlyEncryptedFields() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(new NetEaseApiClient.Response(200,
                "{\"code\":200,\"unikey\":\"qr-key\"}", Collections.emptyList()));
        transport.responses.add(new NetEaseApiClient.Response(200,
                "{\"code\":200,\"unikey\":\"qr-key-2\"}", Collections.emptyList()));
        NetEaseApiClient client = new NetEaseApiClient(transport);

        assertEquals("qr-key", client.createQrLoginKey());
        assertEquals("qr-key-2", client.createQrLoginKey());

        assertEquals(2, transport.paths.size());
        assertTrue(transport.paths.get(0).startsWith("/weapi/login/qrcode/unikey?"));
        assertTrue(transport.paths.get(1).startsWith("/weapi/login/qrcode/unikey?"));
        assertFalse(transport.paths.get(0).equals(transport.paths.get(1)));
        assertEquals(2, transport.forms.get(0).size());
        assertTrue(transport.forms.get(0).containsKey("params"));
        assertTrue(transport.forms.get(0).containsKey("encSecKey"));
    }

    @Test
    public void qrPollMapsWaitingExpiredSuccessAndSecurityStates() throws Exception {
        assertEquals(NetEaseApiClient.QrLoginStatus.WAITING_SCAN,
                qrResult(801, Collections.emptyList()).getStatus());
        assertEquals(NetEaseApiClient.QrLoginStatus.WAITING_CONFIRMATION,
                qrResult(802, Collections.emptyList()).getStatus());
        assertEquals(NetEaseApiClient.QrLoginStatus.EXPIRED,
                qrResult(800, Collections.emptyList()).getStatus());

        NetEaseApiClient.QrLoginResult success = qrResult(803,
                Collections.singletonList("MUSIC_U=qr-session; Path=/; HttpOnly"));
        assertEquals(NetEaseApiClient.QrLoginStatus.AUTHORIZED, success.getStatus());
        assertTrue(success.getCookie().contains("MUSIC_U=qr-session"));

        NetEaseApiClient.QrLoginResult missingCookie = qrResult(803, Collections.emptyList());
        assertEquals(NetEaseApiClient.QrLoginStatus.ERROR, missingCookie.getStatus());
        assertEquals("", missingCookie.getCookie());

        for (int challengeCode : new int[]{8821, -460, -462, 502}) {
            NetEaseApiClient.QrLoginResult challenge = qrResult(challengeCode,
                    Collections.singletonList("MUSIC_U=must-not-be-saved; Path=/"));
            assertEquals(NetEaseApiClient.QrLoginStatus.CHALLENGE, challenge.getStatus());
            assertEquals("", challenge.getCookie());
            assertTrue(challenge.getMessage().contains("官方网易云音乐 App"));
        }
    }

    @Test
    public void qrPollingSendsKeyAndDoesNotReturnCookiesUntilAuthorized() throws Exception {
        final String[] requestPath = {""};
        final String[] requestCookie = {""};
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) -> {
            requestPath[0] = path;
            requestCookie[0] = cookie;
            return new NetEaseApiClient.Response(200, "{\"code\":801}",
                    Collections.singletonList("MUSIC_U=not-yet; Path=/"));
        });

        NetEaseApiClient.QrLoginResult result = client.checkQrLogin("qr-key");

        assertTrue(requestPath[0].startsWith("/weapi/login/qrcode/client/login?noCheckToken=1&"));
        assertTrue(requestCookie[0].isEmpty());
        assertEquals(NetEaseApiClient.QrLoginStatus.WAITING_SCAN, result.getStatus());
        assertEquals("", result.getCookie());
    }

    @Test
    public void qrLoginUrlEncodesAndRejectsAnEmptyKey() {
        assertEquals("https://music.163.com/login?codekey=qr-key",
                NetEaseQrLoginUrl.build("qr-key"));
        assertEquals("https://music.163.com/login?codekey=a%2Bb%2F%3F+%26",
                NetEaseQrLoginUrl.build("a+b/? &"));
        try {
            NetEaseQrLoginUrl.build("");
            throw new AssertionError("Expected empty key to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("key"));
        }
    }

    private static NetEaseApiClient.QrLoginResult qrResult(int code, List<String> cookies)
            throws Exception {
        NetEaseApiClient client = new NetEaseApiClient((path, form, cookie) ->
                new NetEaseApiClient.Response(200, "{\"code\":" + code + "}", cookies));
        return client.checkQrLogin("qr-key");
    }

    private static final class RecordingTransport implements NetEaseApiClient.Transport {
        final List<String> paths = new java.util.ArrayList<>();
        final List<Map<String, String>> forms = new java.util.ArrayList<>();
        final List<NetEaseApiClient.Response> responses = new java.util.ArrayList<>();
        int nextResponse;

        @Override
        public NetEaseApiClient.Response post(String path, Map<String, String> form, String cookie)
                throws IOException {
            paths.add(path);
            forms.add(new HashMap<>(form));
            if (nextResponse < responses.size()) return responses.get(nextResponse++);
            return new NetEaseApiClient.Response(200, "{\"code\":200}",
                    Collections.singletonList("MUSIC_U=session-cookie; Path=/; HttpOnly"));
        }
    }
}
