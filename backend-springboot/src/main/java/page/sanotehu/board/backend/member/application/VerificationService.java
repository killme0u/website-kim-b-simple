package page.sanotehu.board.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetChangeCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetRequestCommand;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;
import page.sanotehu.board.backend.member.domain.MemberStatus;
import page.sanotehu.board.backend.member.domain.VerificationToken;
import page.sanotehu.board.backend.member.domain.VerificationTokenRepository;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final java.util.Random RANDOM = new java.util.Random();

    private final MemberRepository memberRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final CaptchaVerifier captchaVerifier;
    private final PersistentTokenRepository persistentTokenRepository;

    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationToken token = tokenRepository
                .findByTokenHashAndPurpose(TokenHasher.sha256(rawToken), EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 이메일 인증 토큰입니다."));
        if (!token.isUsable(EMAIL_VERIFICATION)) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 이메일 인증 토큰입니다.");
        }
        token.getMember().verifyEmail();
        token.markUsed();
    }

    @Transactional
    public void resendEmailVerification(String email) {
        memberRepository.findByEmail(email)
                .filter(member -> member.getStatus() == MemberStatus.PENDING)
                .ifPresent(this::issueEmailVerification);
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequestCommand command, String remoteAddress) {
        if (!captchaVerifier.verify(command.getCaptchaToken(), remoteAddress)) {
            throw new IllegalArgumentException("CAPTCHA 검증에 실패했습니다.");
        }

        memberRepository.findByEmail(command.getEmail())
                .filter(member -> member.getStatus() != MemberStatus.DELETED)
                .ifPresent(this::issuePasswordReset);
    }

    @Transactional
    public void changePassword(PasswordResetChangeCommand command) {
        VerificationToken token = tokenRepository
                .findByTokenHashAndPurpose(TokenHasher.sha256(command.getToken()), PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 비밀번호 재설정 토큰입니다."));
        if (!token.isUsable(PASSWORD_RESET)) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 비밀번호 재설정 토큰입니다.");
        }
        Member member = token.getMember();
        member.changePassword(passwordEncoder.encode(command.getNewPassword()));
        token.markUsed();
        persistentTokenRepository.removeUserTokens(member.getUsername());
    }

    private void issueEmailVerification(Member member) {
        String rawToken = UUID.randomUUID().toString();
        tokenRepository.save(VerificationToken.emailVerify(
                member, TokenHasher.sha256(rawToken), Duration.ofHours(24)));
        events.publishEvent(new EmailVerificationRequested(member.getEmail(), rawToken));
    }

    private void issuePasswordReset(Member member) {
        String rawToken = UUID.randomUUID().toString();
        tokenRepository.save(VerificationToken.passwordReset(
                member, TokenHasher.sha256(rawToken), Duration.ofHours(1)));
        events.publishEvent(new PasswordResetRequested(member.getEmail(), rawToken));
    }

    @Transactional
    public void issueTempPassword(PasswordResetRequestCommand command, String remoteAddress) {
        if (!captchaVerifier.verify(command.getCaptchaToken(), remoteAddress)) {
            throw new IllegalArgumentException("CAPTCHA 검증에 실패했습니다.");
        }

        memberRepository.findByEmail(command.getEmail())
                .filter(member -> member.getStatus() != MemberStatus.DELETED)
                .ifPresent(this::issueTempPasswordForMember);
    }

    private void issueTempPasswordForMember(Member member) {
        String tempPassword = generateTempPassword();
        ZonedDateTime expiresAt = ZonedDateTime.now().plusHours(1);

        member.setTempPassword(passwordEncoder.encode(tempPassword), expiresAt);
        memberRepository.save(member);

        persistentTokenRepository.removeUserTokens(member.getUsername());

        events.publishEvent(new TempPasswordIssued(member.getEmail(), tempPassword));
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
