package page.sanotehu.board.backend.captcha.adapter.out;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프런트엔드(CaptchaField.tsx)는 언제나 Cloudflare Turnstile 위젯이 발급한 토큰만 보낸다.
 * 따라서 mode=fake 는 실제 사용자를 단 한 명도 통과시킬 수 없다 — 위젯은 "성공!"을 띄우는데
 * 서버는 토큰이 expected-token 과 다르다며 거절한다. 회원가입이 깨졌던 원인이 정확히 이것이다.
 * <p>
 * 이 클래스는 그동안 검증 공백이었다: 다른 테스트는 모두 {@link CaptchaVerifier} 를
 * 목으로 갈아끼워서, 정작 판정을 내리는 구현체는 한 번도 실행되지 않았다.
 */
class ConfiguredCaptchaVerifierTest {

    /** Cloudflare 테스트 site key(1x00000000000000000000AA)가 내놓는 더미 토큰. */
    private static final String TURNSTILE_TOKEN = "XXXX.DUMMY.TOKEN.XXXX";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    private HttpServer server;

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("mode=fake 는 프런트가 보내는 Turnstile 토큰을 거절한다 — 두 설정은 함께 움직여야 한다")
    void fakeModeCannotAcceptTurnstileToken() {
        CaptchaProperties properties = properties("fake");
        properties.setExpectedToken("dev-captcha");
        CaptchaVerifier verifier = new ConfiguredCaptchaVerifier(properties, objectMapper);

        assertThat(verifier.verify(TURNSTILE_TOKEN, "127.0.0.1")).isFalse();
        assertThat(verifier.verify("dev-captcha", "127.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("mode=remote 는 success=true 면 통과시키고 secret·response·remoteip 를 그대로 보낸다")
    void remoteModeAcceptsVerifiedToken() throws IOException {
        CaptchaProperties properties = remoteProperties(200, "{\"success\":true,\"error-codes\":[]}");

        boolean result = new ConfiguredCaptchaVerifier(properties, objectMapper)
                .verify(TURNSTILE_TOKEN, "0:0:0:0:0:0:0:1");

        assertThat(result).isTrue();
        JsonNode sent = objectMapper.readTree(lastRequestBody.get());
        assertThat(sent.path("secret").asString()).isEqualTo("test-secret");
        assertThat(sent.path("response").asString()).isEqualTo(TURNSTILE_TOKEN);
        // 로컬 접속은 IPv6 루프백으로 잡힌다. Cloudflare 는 이 값도 받아준다.
        assertThat(sent.path("remoteip").asString()).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("mode=remote 는 success=false 면 거절한다")
    void remoteModeRejectsUnverifiedToken() throws IOException {
        CaptchaProperties properties = remoteProperties(
                200, "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");

        assertThat(new ConfiguredCaptchaVerifier(properties, objectMapper)
                .verify(TURNSTILE_TOKEN, "127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("검증 서버가 5xx 를 주면 거절한다 — 장애 시 열리지 않는다")
    void remoteModeFailsClosedOnServerError() throws IOException {
        CaptchaProperties properties = remoteProperties(500, "upstream down");

        assertThat(new ConfiguredCaptchaVerifier(properties, objectMapper)
                .verify(TURNSTILE_TOKEN, "127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("mode=remote 인데 endpoint·secret 이 비면 거절한다 — 설정 누락이 무방비로 이어지지 않는다")
    void remoteModeFailsClosedWhenMisconfigured() {
        CaptchaProperties missingBoth = properties("remote");
        CaptchaProperties missingSecret = properties("remote");
        missingSecret.setEndpoint("https://example.invalid/siteverify");

        assertThat(new ConfiguredCaptchaVerifier(missingBoth, objectMapper)
                .verify(TURNSTILE_TOKEN, "127.0.0.1")).isFalse();
        assertThat(new ConfiguredCaptchaVerifier(missingSecret, objectMapper)
                .verify(TURNSTILE_TOKEN, "127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("빈 토큰은 검증 서버를 부르지 않고 즉시 거절한다")
    void blankTokenIsRejectedWithoutCallingServer() {
        CaptchaProperties properties = properties("remote");
        properties.setEndpoint("https://example.invalid/siteverify");
        properties.setSecret("test-secret");
        CaptchaVerifier verifier = new ConfiguredCaptchaVerifier(properties, objectMapper);

        assertThat(verifier.verify(null, "127.0.0.1")).isFalse();
        assertThat(verifier.verify("   ", "127.0.0.1")).isFalse();
    }

    private CaptchaProperties properties(String mode) {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setMode(mode);
        properties.setTimeout(Duration.ofSeconds(3));
        return properties;
    }

    /** 검증 서버 응답을 고정한 스텁을 띄우고, 그 주소를 가리키는 remote 설정을 만든다. */
    private CaptchaProperties remoteProperties(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();

        CaptchaProperties properties = properties("remote");
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
        properties.setSecret("test-secret");
        return properties;
    }
}
