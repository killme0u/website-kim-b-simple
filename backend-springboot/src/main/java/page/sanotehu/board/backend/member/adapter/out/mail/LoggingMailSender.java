package page.sanotehu.board.backend.member.adapter.out.mail;

import lombok.extern.slf4j.Slf4j;
import page.sanotehu.board.backend.member.application.MailSenderPort;

/**
 * SMTP가 설정되지 않은 개발 환경용 폴백. 실제로 발송하지 않고 본문을 로그에 남긴다.
 * 인증 링크가 로그에 남으므로 운영 환경에서는 {@code spring.mail.host}를 반드시 설정해야 한다.
 */
@Slf4j
public class LoggingMailSender implements MailSenderPort {

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("[MAIL:DRY-RUN] to={} subject={}\n{}", to, subject, htmlBody);
    }
}
