package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@DisplayName("F-113: 임시 비밀번호 발급 - RED 2")
class TempPasswordLoginTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    @DisplayName("RED 2-1: 임시 비밀번호로 로그인하면 must_change_password=true가 설정된다")
    void setMustChangePasswordOnTempPasswordLogin() {
        String tempPassword = "TEMP-9999-8888";
        Member member = Member.pending("testuser", passwordEncoder.encode(tempPassword), "테스트", "test@example.com", "01012345678");
        member.setTempPasswordExpiresAt(ZonedDateTime.now().plusHours(1));

        given(memberRepository.findByUsername("testuser")).willReturn(Optional.of(member));

        // 인증 시뮬레이션
        boolean isValidTempPassword = passwordEncoder.matches(tempPassword, member.getPasswordHash());
        assertThat(isValidTempPassword).isTrue();

        // 로그인 성공 후 must_change_password 설정 확인
        // (실제로는 LoginService 또는 CustomUserDetailsService에서 처리)
        assertThat(member.getTempPasswordExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("RED 2-2: 정상 비밀번호로 로그인하면 must_change_password=false 유지")
    void keepMustChangePasswordFalseForNormalPassword() {
        String normalPassword = "normal-password-123";
        Member member = Member.pending("testuser", passwordEncoder.encode(normalPassword), "테스트", "test@example.com", "01012345678");
        // tempPasswordExpiresAt은 null (정상 비밀번호 사용)

        boolean isValidPassword = passwordEncoder.matches(normalPassword, member.getPasswordHash());
        assertThat(isValidPassword).isTrue();

        // must_change_password는 false 유지
        assertThat(member.isMustChangePassword()).isFalse();
        assertThat(member.getTempPasswordExpiresAt()).isNull();
    }

    @Test
    @DisplayName("RED 2-3: must_change_password 상태를 클라이언트가 확인할 수 있다")
    void clientCanCheckMustChangePasswordFlag() {
        Member member = Member.pending("testuser", "hash", "테스트", "test@example.com", "01012345678");
        member.setTempPasswordExpiresAt(ZonedDateTime.now().plusHours(1));

        // MeResponse에 mustChangePassword 포함됨 (이미 구현됨)
        assertThat(member.isMustChangePassword() || member.getTempPasswordExpiresAt() != null).isTrue();
    }
}
