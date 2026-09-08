package page.sanotehu.board.backend.captcha.adapter.out;

import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

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
            return false;
        }
        if ("fake".equalsIgnoreCase(properties.getMode())) {
            return token.equals(properties.getExpectedToken());
        }
        if (!"remote".equalsIgnoreCase(properties.getMode())
                || properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getSecret() == null || properties.getSecret().isBlank()) {
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
                return false;
            }
            JsonNode body = objectMapper.readTree(response.body());
            return body.path("success").asBoolean(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
