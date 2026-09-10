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

import java.util.Locale;
import java.util.Map;

/**
 * F-113: 임시 비밀번호 발급 이벤트를 받아 메일을 발송한다.
 * 커밋 이후(AFTER_COMMIT) 비동기로 실행되므로 메일 실패가 트랜잭션을 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempPasswordListener {

    private static final String SUBJECT_TEMP_PASSWORD = "[BoardSystem] 임시 비밀번호 발급";

    private final MailSenderPort mailSender;
    private final ITemplateEngine templateEngine;
    private final AppMailProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTempPasswordIssued(TempPasswordIssued event) {
        send(event.getEmail(), SUBJECT_TEMP_PASSWORD, "mail/temp-password", Map.of(
                "tempPassword", event.getTempPassword(),
                "expiresIn", "1시간",
                "loginUrl", properties.getBaseUrl() + "/login",
                "changePasswordUrl", properties.getBaseUrl() + "/me"
        ));
    }

    private void send(String to, String subject, String template, Map<String, Object> variables) {
        try {
            Context context = new Context(Locale.KOREA, variables);
            mailSender.send(to, subject, templateEngine.process(template, context));
        } catch (RuntimeException e) {
            log.error("임시 비밀번호 메일 발송에 실패했습니다. to={} subject={}", to, subject, e);
        }
    }
}
