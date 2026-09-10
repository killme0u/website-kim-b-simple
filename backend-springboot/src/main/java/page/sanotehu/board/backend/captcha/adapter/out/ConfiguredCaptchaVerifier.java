package page.sanotehu.board.backend.captcha.adapter.out;

import lombok.extern.slf4j.Slf4j;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
public class ConfiguredCaptchaVerifier implements CaptchaVerifier {

    private final CaptchaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ConfiguredCaptchaVerifier(CaptchaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public boolean verify(String token, String remoteAddress) {
        if (token == null || token.isBlank()) {
            log.debug("CAPTCHA verification failed: empty token. remoteAddress={}", remoteAddress);
            return false;
        }
        if ("fake".equalsIgnoreCase(properties.getMode())) {
            boolean result = token.equals(properties.getExpectedToken());
            log.debug("CAPTCHA verification (fake mode): result={}. remoteAddress={}", result, remoteAddress);
            return result;
        }
        if (!"remote".equalsIgnoreCase(properties.getMode())
                || properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getSecret() == null || properties.getSecret().isBlank()) {
            log.warn("CAPTCHA verification failed: invalid configuration. mode={} endpoint={}",
                    properties.getMode(), properties.getEndpoint() != null ? "configured" : "missing");
            return false;
        }

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "secret", properties.getSecret(),
                    "response", token,
                    "remoteip", remoteAddress == null ? "" : remoteAddress
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("CAPTCHA server returned error: status={}. remoteAddress={}", response.statusCode(), remoteAddress);
                return false;
            }
            JsonNode body = objectMapper.readTree(response.body());
            boolean success = body.path("success").asBoolean(false);
            if (!success) {
                log.debug("CAPTCHA verification failed: server returned success=false. remoteAddress={}", remoteAddress);
            } else {
                log.debug("CAPTCHA verification succeeded. remoteAddress={}", remoteAddress);
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("CAPTCHA verification interrupted. remoteAddress={}", remoteAddress);
            return false;
        } catch (IOException | RuntimeException e) {
            log.error("CAPTCHA verification error. remoteAddress={}", remoteAddress, e);
            return false;
        }
    }
}
