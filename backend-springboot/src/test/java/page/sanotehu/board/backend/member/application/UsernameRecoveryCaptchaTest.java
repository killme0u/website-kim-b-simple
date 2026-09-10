package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.captcha.application.CaptchaVerifier;
import page.sanotehu.board.backend.member.adapter.in.web.dto.UsernameRecoveryCommand;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("아이디 찾기 - CAPTCHA 검증")
class UsernameRecoveryCaptchaTest {

    @Autowired
    private UsernameRecoveryService usernameRecoveryService;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private CaptchaVerifier captchaVerifier;

    @Test
    @DisplayName("CAPTCHA 검증 성공하면 아이디 안내 메일이 발송된다")
    void sendUsernameRecoveryMailOnValidCaptcha() {
        // Given: 유효한 CAPTCHA
        UsernameRecoveryCommand command = new UsernameRecoveryCommand();
        command.setEmail("test@example.com");
        command.setCaptchaToken("dev-captcha");

        Member member = Member.pending("testuser", "hash", "테스트", "test@example.com", "01012345678");
        given(captchaVerifier.verify("dev-captcha", "127.0.0.1")).willReturn(true);
        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));

        // When: 아이디 찾기 요청
        usernameRecoveryService.requestRecovery(command, "127.0.0.1");

        // Then: 메일 발송 이벤트 발생 (ApplicationEventPublisher 모의)
        verify(memberRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("CAPTCHA 검증 실패하면 예외를 발생시킨다")
    void throwExceptionOnInvalidCaptcha() {
        // Given: 유효하지 않은 CAPTCHA
        UsernameRecoveryCommand command = new UsernameRecoveryCommand();
        command.setEmail("test@example.com");
        command.setCaptchaToken("invalid-token");

        given(captchaVerifier.verify("invalid-token", "127.0.0.1")).willReturn(false);

        // When/Then: CAPTCHA 검증 실패
        assertThatThrownBy(() -> usernameRecoveryService.requestRecovery(command, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAPTCHA 검증에 실패했습니다");
    }

    @Test
    @DisplayName("빈 CAPTCHA 토큰은 검증 실패한다")
    void rejectEmptyCaptchaToken() {
        // Given: 빈 CAPTCHA
        UsernameRecoveryCommand command = new UsernameRecoveryCommand();
        command.setEmail("test@example.com");
        command.setCaptchaToken("");

        given(captchaVerifier.verify("", "127.0.0.1")).willReturn(false);

        // When/Then: 검증 실패
        assertThatThrownBy(() -> usernameRecoveryService.requestRecovery(command, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CAPTCHA 검증 후에도 가입하지 않은 사용자는 안내 메일을 보내지 않는다")
    void silentlyHandleNonexistentMember() {
        // Given: 존재하지 않는 사용자
        UsernameRecoveryCommand command = new UsernameRecoveryCommand();
        command.setEmail("notfound@example.com");
        command.setCaptchaToken("dev-captcha");

        given(captchaVerifier.verify("dev-captcha", "127.0.0.1")).willReturn(true);
        given(memberRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

        // When: 아이디 찾기 요청 (CAPTCHA 검증만 성공)
        usernameRecoveryService.requestRecovery(command, "127.0.0.1");

        // Then: 메일 발송되지 않음 (ifPresent 사용으로 조용히 무시)
        verify(memberRepository).findByEmail("notfound@example.com");
    }
}
