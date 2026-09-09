package page.sanotehu.board.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.SignupCommand;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;
import page.sanotehu.board.backend.member.domain.VerificationToken;
import page.sanotehu.board.backend.member.domain.VerificationTokenRepository;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final MemberRepository memberRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final CaptchaVerifier captchaVerifier;

    @Transactional
    public Long signup(SignupCommand cmd, String remoteAddress) {
        if (!captchaVerifier.verify(cmd.getCaptchaToken(), remoteAddress)) {
            throw new IllegalArgumentException("CAPTCHA 검증에 실패했습니다.");
        }
        // 닉네임 정규화(trim, 빈 값 → null)는 Member.pending이 수행한다.
        Member member = Member.pending(
            cmd.getUsername(), encoder.encode(cmd.getPassword()),
            cmd.getName(), cmd.getNickname(), cmd.getEmail(), cmd.getPhone()
        );
        memberRepository.save(member);

        String rawToken = UUID.randomUUID().toString();
        VerificationToken token = VerificationToken.emailVerify(
                member, TokenHasher.sha256(rawToken), Duration.ofHours(24));
        tokenRepository.save(token);

        events.publishEvent(new SignupCompleted(member.getEmail(), rawToken));
        return member.getId();
    }

    public Long signup(SignupCommand cmd) {
        return signup(cmd, null);
    }
}