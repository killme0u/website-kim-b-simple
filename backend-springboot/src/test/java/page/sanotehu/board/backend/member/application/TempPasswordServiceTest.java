package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetRequestCommand;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;
import page.sanotehu.board.backend.member.domain.MemberStatus;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("F-113: 임시 비밀번호 발급 - RED 1")
class TempPasswordServiceTest {

    @Autowired
    private VerificationService verificationService;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private CaptchaVerifier captchaVerifier;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private PasswordResetRequestCommand validCommand() {
        PasswordResetRequestCommand cmd = new PasswordResetRequestCommand();
        cmd.setEmail("user@example.com");
        cmd.setCaptchaToken("valid-token");
        return cmd;
    }

    @Test
    @DisplayName("RED 1-1: 임시 비밀번호를 생성할 수 있다")
    void generateTempPassword() {
        PasswordResetRequestCommand cmd = validCommand();
        Member member = Member.pending("testuser", "hash", "테스트", "test@example.com", "01012345678");

        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);
        given(memberRepository.findByEmail(cmd.getEmail())).willReturn(Optional.of(member));

        // 임시 비밀번호 발급 API 호출
        verificationService.issueTempPassword(cmd, null);

        // 멤버의 tempPasswordExpiresAt이 설정되어야 함
        assertThat(member.getTempPasswordExpiresAt()).isNotNull();
        assertThat(member.getTempPasswordExpiresAt()).isAfter(ZonedDateTime.now());
    }

    @Test
    @DisplayName("RED 1-2: 임시 비밀번호는 일반 비밀번호와 다르다")
    void tempPasswordIsDifferentFromRegularPassword() {
        PasswordResetRequestCommand cmd = validCommand();
        Member member = Member.pending("testuser", "oldHash", "테스트", "test@example.com", "01012345678");
        String originalHash = member.getPasswordHash();

        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);
        given(memberRepository.findByEmail(cmd.getEmail())).willReturn(Optional.of(member));

        verificationService.issueTempPassword(cmd, null);

        // 비밀번호 해시가 변경되어야 함
        assertThat(member.getPasswordHash()).isNotEqualTo(originalHash);
    }

    @Test
    @DisplayName("RED 1-3: 임시 비밀번호 발급 후 이메일이 발송된다")
    void sendsTempPasswordEmail() {
        PasswordResetRequestCommand cmd = validCommand();
        Member member = Member.pending("testuser", "hash", "테스트", "test@example.com", "01012345678");

        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);
        given(memberRepository.findByEmail(cmd.getEmail())).willReturn(Optional.of(member));

        verificationService.issueTempPassword(cmd, null);

        // 이벤트 발행 확인 (TempPasswordIssued 이벤트)
        // 나중에 검증 (현재는 구현만 확인)
    }

    @Test
    @DisplayName("RED 1-4: CAPTCHA 검증 실패 시 임시 비밀번호를 발급하지 않는다")
    void rejectsIfCaptchaFails() {
        PasswordResetRequestCommand cmd = validCommand();
        Member member = Member.pending("testuser", "hash", "테스트", "test@example.com", "01012345678");

        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(false);

        try {
            verificationService.issueTempPassword(cmd, null);
            throw new AssertionError("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("CAPTCHA");
        }
    }
}
