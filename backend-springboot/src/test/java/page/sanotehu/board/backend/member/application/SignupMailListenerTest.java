package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.thymeleaf.ITemplateEngine;
import page.sanotehu.board.backend.member.adapter.out.mail.AppMailProperties;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignupMailListenerTest {

    private static final String TOKEN = "7f3a1b2c-token";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThymeleafAutoConfiguration.class));

    private record SentMail(String to, String subject, String html) {}

    private static final class RecordingMailSender implements MailSenderPort {
        private final List<SentMail> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String htmlBody) {
            sent.add(new SentMail(to, subject, htmlBody));
        }
    }

    private static AppMailProperties properties() {
        AppMailProperties properties = new AppMailProperties();
        properties.setBaseUrl("https://board.example");
        return properties;
    }

    @Test
    @DisplayName("Thymeleaf 자동 설정이 메일 렌더링에 쓸 템플릿 엔진을 등록한다")
    void templateEngineIsAvailable() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ITemplateEngine.class));
    }

    @Test
    @DisplayName("회원가입 이벤트에 인증 링크와 인증 코드가 담긴 메일을 보낸다")
    void sendsSignupVerificationMail() {
        contextRunner.run(context -> {
            RecordingMailSender mailSender = new RecordingMailSender();
            SignupMailListener listener = new SignupMailListener(
                    mailSender, context.getBean(ITemplateEngine.class), properties());

            listener.onSignupCompleted(new SignupCompleted("member@example.com", TOKEN));

            assertThat(mailSender.sent).singleElement().satisfies(mail -> {
                assertThat(mail.to()).isEqualTo("member@example.com");
                assertThat(mail.subject()).contains("회원가입 인증");
                assertThat(mail.html()).contains("https://board.example/verify-email?token=" + TOKEN);
                assertThat(mail.html()).contains(TOKEN);
            });
        });
    }

    @Test
    @DisplayName("비밀번호 재설정·아이디 찾기 메일도 같은 발송 경로를 탄다")
    void sendsAccountRecoveryMails() {
        contextRunner.run(context -> {
            RecordingMailSender mailSender = new RecordingMailSender();
            SignupMailListener listener = new SignupMailListener(
                    mailSender, context.getBean(ITemplateEngine.class), properties());

            listener.onPasswordResetRequested(new PasswordResetRequested("member@example.com", TOKEN));
            listener.onUsernameRecoveryRequested(new UsernameRecoveryRequested("member@example.com", "hong"));

            assertThat(mailSender.sent).hasSize(2);
            assertThat(mailSender.sent.get(0).html())
                    .contains("https://board.example/find-password?token=" + TOKEN);
            assertThat(mailSender.sent.get(1).html()).contains("hong");
        });
    }
}
