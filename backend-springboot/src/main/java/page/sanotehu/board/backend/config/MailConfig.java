package page.sanotehu.board.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;
import page.sanotehu.board.backend.member.adapter.out.mail.AppMailProperties;
import page.sanotehu.board.backend.member.adapter.out.mail.LoggingMailSender;
import page.sanotehu.board.backend.member.adapter.out.mail.SmtpMailSender;
import page.sanotehu.board.backend.member.application.MailSenderPort;

@Slf4j
@Configuration
@EnableConfigurationProperties(AppMailProperties.class)
public class MailConfig {

    /**
     * SMTP 호스트와 계정이 모두 채워진 경우에만 {@link SmtpMailSender}를 쓰고,
     * 아니면 로그 전용 구현으로 폴백해 개발 환경에서 가입 흐름이 막히지 않도록 한다.
     * <p>
     * {@code application.yml}이 {@code spring.mail.host: ${MAIL_SMTP_HOST:}}로 기본값을 주기 때문에
     * {@code .env} 없이도 속성 자체는 항상 존재한다. Spring Boot의 자동 설정 조건은 값이 비어 있어도
     * 충족되므로 {@link JavaMailSender} 빈의 유무만으로는 판단할 수 없다. 값을 직접 확인한다.
     * <p>
     * 계정까지 보는 이유는 {@code .env.example}을 그대로 복사한 직후처럼 호스트만 있고 자격 증명이 빈
     * 상태에서, 기동은 성공한 뒤 가입 시점에야 SMTP 인증 실패로 터지는 상황을 막기 위해서다.
     * {@code spring.mail.properties.mail.smtp.auth}가 항상 {@code true}라 계정 없이는 어차피 발송이 불가능하다.
     */
    @Bean
    MailSenderPort mailSenderPort(ObjectProvider<JavaMailSender> javaMailSender,
                                  AppMailProperties properties,
                                  @Value("${spring.mail.host:}") String host,
                                  @Value("${spring.mail.username:}") String username) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null || !StringUtils.hasText(host) || !StringUtils.hasText(username)) {
            log.warn("SMTP 설정이 비어 있어(host='{}', username 설정됨={}) 메일을 실제로 발송하지 않습니다. "
                            + "본문은 로그로만 남습니다. 실제 발송이 필요하면 저장소 루트 .env의 "
                            + "MAIL_SMTP_HOST/MAIL_USERNAME/MAIL_PASSWORD를 채우세요.",
                    host, StringUtils.hasText(username));
            return new LoggingMailSender();
        }
        log.info("SMTP 메일 발송을 사용합니다. host={} from={}", host, properties.getFrom());
        return new SmtpMailSender(sender, properties.getFrom());
    }
}
