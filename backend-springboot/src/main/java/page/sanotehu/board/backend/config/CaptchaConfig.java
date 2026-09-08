package page.sanotehu.board.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import page.sanotehu.board.backend.captcha.adapter.out.CaptchaProperties;
import page.sanotehu.board.backend.captcha.adapter.out.ConfiguredCaptchaVerifier;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfig {

    @Bean
    CaptchaVerifier captchaVerifier(CaptchaProperties properties, ObjectMapper objectMapper) {
        return new ConfiguredCaptchaVerifier(properties, objectMapper);
    }
}
