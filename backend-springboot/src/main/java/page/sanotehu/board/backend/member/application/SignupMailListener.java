package page.sanotehu.board.backend.member.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import page.sanotehu.board.backend.member.adapter.out.mail.AppMailProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 회원 도메인 이벤트를 받아 안내 메일을 발송한다.
 * 커밋 이후(AFTER_COMMIT) 비동기로 실행되므로 메일 실패가 가입 트랜잭션을 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignupMailListener {

    private static final String SUBJECT_EMAIL_VERIFICATION = "[BoardSystem] 회원가입 인증을 완료해 주세요";
    private static final String SUBJECT_PASSWORD_RESET = "[BoardSystem] 비밀번호 재설정 안내";
    private static final String SUBJECT_USERNAME_RECOVERY = "[BoardSystem] 아이디 찾기 안내";

    private final MailSenderPort mailSender;
    private final ITemplateEngine templateEngine;
    private final AppMailProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignupCompleted(SignupCompleted event) {
        sendEmailVerification(event.email(), event.rawToken());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailVerificationRequested(EmailVerificationRequested event) {
        sendEmailVerification(event.email(), event.rawToken());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        send(event.email(), SUBJECT_PASSWORD_RESET, "mail/password-reset", Map.of(
                "token", event.rawToken(),
                "actionUrl", link("/find-password", event.rawToken()),
                "expiresIn", "1시간"
        ));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUsernameRecoveryRequested(UsernameRecoveryRequested event) {
        send(event.email(), SUBJECT_USERNAME_RECOVERY, "mail/username-recovery", Map.of(
                "username", event.username(),
                "loginUrl", properties.getBaseUrl() + "/login"
        ));
    }

    private void sendEmailVerification(String email, String rawToken) {
        send(email, SUBJECT_EMAIL_VERIFICATION, "mail/email-verification", Map.of(
                "token", rawToken,
                "actionUrl", link("/verify-email", rawToken),
                "expiresIn", "24시간"
        ));
    }

    private void send(String to, String subject, String template, Map<String, Object> variables) {
        try {
            Context context = new Context(Locale.KOREA, variables);
            mailSender.send(to, subject, templateEngine.process(template, context));
        } catch (RuntimeException e) {
            // 비동기 리스너에서 예외가 나면 호출자에게 전달되지 않으므로 여기서 남긴다.
            log.error("메일 발송에 실패했습니다. to={} subject={}", to, subject, e);
        }
    }

    private String link(String path, String rawToken) {
        return "%s%s?token=%s".formatted(
                properties.getBaseUrl(), path, URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
    }
}
