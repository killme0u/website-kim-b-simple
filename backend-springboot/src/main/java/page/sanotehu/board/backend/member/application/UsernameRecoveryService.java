package page.sanotehu.board.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.UsernameRecoveryCommand;
import page.sanotehu.board.backend.member.domain.MemberStatus;
import page.sanotehu.board.backend.member.domain.MemberRepository;

@Service
@RequiredArgsConstructor
public class UsernameRecoveryService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher events;
    private final CaptchaVerifier captchaVerifier;

    @Transactional(readOnly = true)
    public void requestRecovery(UsernameRecoveryCommand command, String remoteAddress) {
        if (!captchaVerifier.verify(command.getCaptchaToken(), remoteAddress)) {
            throw new IllegalArgumentException("CAPTCHA 검증에 실패했습니다.");
        }

        memberRepository.findByEmail(command.getEmail())
                .filter(member -> member.getStatus() != MemberStatus.DELETED)
                .ifPresent(member -> events.publishEvent(
                        new UsernameRecoveryRequested(member.getEmail(), member.getUsername())));
    }
}
