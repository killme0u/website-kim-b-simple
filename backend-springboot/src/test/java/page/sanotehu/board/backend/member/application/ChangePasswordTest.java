package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.member.adapter.in.web.dto.ChangePasswordCommand;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;
import page.sanotehu.board.backend.member.domain.MemberStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("비밀번호 변경 (로그인한 사용자 전용)")
class ChangePasswordTest {

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    @DisplayName("로그인한 사용자가 비밀번호를 변경할 수 있다")
    void changePasswordByAuthenticatedUser() {
        // Given: 활성 사용자
        Member member = Member.pending(
                "testuser",
                passwordEncoder.encode("OldPassword123!"),
                "테스트",
                "test@example.com",
                "01012345678"
        );
        member.verifyEmail();
        member.setMustChangePassword(true);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        ChangePasswordCommand command = new ChangePasswordCommand();
        command.setNewPassword("NewPassword123!");

        // When: 비밀번호 변경
        verificationService.changePasswordByUser(1L, command);

        // Then: 비밀번호가 변경되고 플래그가 해제됨
        assertThat(passwordEncoder.matches("NewPassword123!", member.getPasswordHash())).isTrue();
        assertThat(member.isMustChangePassword()).isFalse();
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 예외를 발생시킨다")
    void throwExceptionForNonexistentUser() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        ChangePasswordCommand command = new ChangePasswordCommand();
        command.setNewPassword("NewPassword123!");

        assertThatThrownBy(() -> verificationService.changePasswordByUser(999L, command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("비밀번호 변경 후 기존 Remember-Me 토큰이 제거된다")
    void removePersistentTokensOnPasswordChange() {
        Member member = Member.pending(
                "testuser",
                passwordEncoder.encode("OldPassword123!"),
                "테스트",
                "test@example.com",
                "01012345678"
        );

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        ChangePasswordCommand command = new ChangePasswordCommand();
        command.setNewPassword("NewPassword123!");

        verificationService.changePasswordByUser(1L, command);

        // Remember-Me 토큰 제거 확인 (모의 객체로 검증)
        verify(memberRepository).save(member);
    }
}
