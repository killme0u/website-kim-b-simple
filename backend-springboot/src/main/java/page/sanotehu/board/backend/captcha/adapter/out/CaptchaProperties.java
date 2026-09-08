package page.sanotehu.board.backend.captcha.adapter.out;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.captcha")
public class CaptchaProperties {

    private String mode = "fake";
    private String endpoint;
    private String secret;
    private String expectedToken = "dev-captcha";
    private Duration timeout = Duration.ofSeconds(3);
}
