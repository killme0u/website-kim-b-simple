package page.sanotehu.board.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import page.sanotehu.board.backend.member.adapter.out.mail.LoggingMailSender;
import page.sanotehu.board.backend.member.adapter.out.mail.SmtpMailSender;
import page.sanotehu.board.backend.member.application.MailSenderPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code application.yml}이 {@code spring.mail.host}에 빈 문자열 기본값을 주기 때문에,
 * 속성은 항상 존재하고 Spring Boot의 자동 설정 조건도 항상 충족된다.
 * 실제 발송 여부를 값으로 판단하는지 확인한다.
 */
class MailConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class);

    @Test
    @DisplayName(".env 없이 기본값만 있으면 로그 전용 발송기로 폴백한다")
    void fallsBackWhenHostIsBlank() {
        contextRunner.withPropertyValues("spring.mail.host=", "spring.mail.username=")
                .run(context -> assertThat(context.getBean(MailSenderPort.class))
                        .isInstanceOf(LoggingMailSender.class));
    }

    @Test
    @DisplayName(".env.example을 복사만 하고 계정을 비워 둔 상태에서도 로그 전용으로 폴백한다")
    void fallsBackWhenCredentialsAreBlank() {
        contextRunner.withPropertyValues("spring.mail.host=smtp.naver.com", "spring.mail.username=")
                .run(context -> assertThat(context.getBean(MailSenderPort.class))
                        .isInstanceOf(LoggingMailSender.class));
    }

    @Test
    @DisplayName("호스트와 계정이 모두 채워지면 실제 SMTP 발송기를 쓴다")
    void usesSmtpWhenFullyConfigured() {
        contextRunner.withPropertyValues(
                        "spring.mail.host=smtp.naver.com",
                        "spring.mail.port=465",
                        "spring.mail.username=board@naver.com",
                        "spring.mail.password=app-password",
                        "app.mail.from=admin@example.com")
                .run(context -> assertThat(context.getBean(MailSenderPort.class))
                        .isInstanceOf(SmtpMailSender.class));
    }
}
