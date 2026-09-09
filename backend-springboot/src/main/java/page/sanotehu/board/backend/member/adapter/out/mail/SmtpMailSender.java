package page.sanotehu.board.backend.member.adapter.out.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import page.sanotehu.board.backend.member.application.MailSenderPort;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class SmtpMailSender implements MailSenderPort {

    private final JavaMailSender javaMailSender;
    private final String from;

    @Override
    public void send(String to, String subject, String htmlBody) {
        javaMailSender.send(mimeMessage -> {
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
        });
    }
}
