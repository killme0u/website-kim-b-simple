package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.SignupCommand;
import page.sanotehu.board.backend.member.domain.MemberRepository;
import page.sanotehu.board.backend.member.domain.VerificationTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class SignupServiceTest {

    @Autowired
    private SignupService signupService;

    @MockitoBean
    private CaptchaVerifier captchaVerifier;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private VerificationTokenRepository tokenRepository;

    @MockitoBean
    private ApplicationEventPublisher events;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SignupCommand validCommand() {
        SignupCommand cmd = new SignupCommand();
        cmd.setUsername("testuser");
        cmd.setPassword("password123");
        cmd.setName("테스트 사용자");
        cmd.setNickname("테스트닉네임");
        cmd.setEmail("test@example.com");
        cmd.setPhone("01012345678");
        cmd.setCaptchaToken("valid-token");
        return cmd;
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 회원가입이 실패한다")
    void rejectSignupWithoutTermsAccepted() {
        SignupCommand cmd = validCommand();
        cmd.setTermsAccepted(false);
        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("약관");
    }

    @Test
    @DisplayName("약관 동의가 null이면 회원가입이 실패한다")
    void rejectSignupWithNullTermsAccepted() {
        SignupCommand cmd = validCommand();
        cmd.setTermsAccepted(null);
        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("약관");
    }

    @Test
    @DisplayName("약관에 동의하면 회원가입이 진행된다")
    void acceptSignupWithTermsAccepted() {
        SignupCommand cmd = validCommand();
        cmd.setTermsAccepted(true);
        given(captchaVerifier.verify(cmd.getCaptchaToken(), null)).willReturn(true);
        given(memberRepository.save(org.mockito.ArgumentMatchers.any())).will(invocation -> {
            var member = invocation.getArgument(0);
            invocation.getMock();
            return member;
        });

        Long memberId = signupService.signup(cmd);

        assertThat(memberId).isNotNull();
    }
}
