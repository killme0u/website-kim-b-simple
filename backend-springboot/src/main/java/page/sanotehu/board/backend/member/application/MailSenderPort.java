package page.sanotehu.board.backend.member.application;

/**
 * 회원 관련 알림 메일 발송 포트.
 *
 * <p>구현체는 SMTP 설정({@code spring.mail.host}) 유무에 따라 런타임에 선택된다.
 * 설정이 없으면 로그로만 남기는 구현이 주입되므로 개발 환경에서도 가입 흐름이 끊기지 않는다.
 */
public interface MailSenderPort {

    void send(String to, String subject, String htmlBody);
}
