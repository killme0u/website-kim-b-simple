package page.sanotehu.board.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import page.sanotehu.board.backend.member.adapter.out.mail.AppMailProperties;
import page.sanotehu.board.backend.member.adapter.out.mail.LoggingMailSender;
import page.sanotehu.board.backend.member.adapter.out.mail.SmtpMailSender;
import page.sanotehu.board.backend.member.application.MailSenderPort;

@Slf4j
@Configuration
@EnableConfigurationProperties(AppMailProperties.class)
public class MailConfig {

    /**
     * {@code spring.mail.host}가 설정된 경우에만 Spring Boot가 {@link JavaMailSender}를 등록한다.
     * 없으면 로그 전용 구현으로 폴백해 개발 환경에서 가입 흐름이 막히지 않도록 한다.
     */
    @Bean
    MailSenderPort mailSenderPort(ObjectProvider<JavaMailSender> javaMailSender, AppMailProperties properties) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host가 비어 있어 메일을 실제로 발송하지 않습니다. 본문은 로그로만 남습니다.");
            return new LoggingMailSender();
        }
        return new SmtpMailSender(sender, properties.getFrom());
    }
}
