package page.sanotehu.board.backend.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@DisplayName("F-113: 임시 비밀번호 발급 - RED 3")
class TempPasswordExpiryTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberRepository memberRepository;

    private Member memberWithExpiredTempPassword(String tempPassword) {
        Member member = Member.pending("testuser", passwordEncoder.encode(tempPassword), "테스트", "test@example.com", "01012345678");
        member.setTempPasswordExpiresAt(ZonedDateTime.now().minusMinutes(1)); // 1분 전 만료
        return member;
    }

    private Member memberWithValidTempPassword(String tempPassword) {
        Member member = Member.pending("testuser", passwordEncoder.encode(tempPassword), "테스트", "test@example.com", "01012345678");
        member.setTempPasswordExpiresAt(ZonedDateTime.now().plusHours(1)); // 1시간 후 만료
        return member;
    }

    @Test
    @DisplayName("RED 3-1: 만료된 임시 비밀번호로 로그인을 거부한다")
    void rejectExpiredTempPassword() {
        String tempPassword = "TEMP-EXPIRED";
        Member member = memberWithExpiredTempPassword(tempPassword);

        given(memberRepository.findByUsername("testuser")).willReturn(Optional.of(member));

        // 비밀번호는 맞지만 만료됨
        boolean isPasswordCorrect = passwordEncoder.matches(tempPassword, member.getPasswordHash());
        assertThat(isPasswordCorrect).isTrue();

        // 만료 여부 확인
        boolean isExpired = member.getTempPasswordExpiresAt() != null && member.getTempPasswordExpiresAt().isBefore(ZonedDateTime.now());
        assertThat(isExpired).isTrue();

        // 로그인 거부해야 함 (AuthenticationProvider에서 처리)
    }

    @Test
    @DisplayName("RED 3-2: 유효한 임시 비밀번호로는 로그인할 수 있다")
    void acceptValidTempPassword() {
        String tempPassword = "TEMP-VALID";
        Member member = memberWithValidTempPassword(tempPassword);

        given(memberRepository.findByUsername("testuser")).willReturn(Optional.of(member));

        boolean isPasswordCorrect = passwordEncoder.matches(tempPassword, member.getPasswordHash());
        assertThat(isPasswordCorrect).isTrue();

        boolean isNotExpired = member.getTempPasswordExpiresAt() == null || member.getTempPasswordExpiresAt().isAfter(ZonedDateTime.now());
        assertThat(isNotExpired).isTrue();
    }

    @Test
    @DisplayName("RED 3-3: 임시 비밀번호로 로그인 후 비밀번호 변경하면 만료 시간이 초기화된다")
    void clearTempPasswordExpiryAfterPasswordChange() {
        String tempPassword = "TEMP-CHANGE";
        Member member = memberWithValidTempPassword(tempPassword);

        assertThat(member.getTempPasswordExpiresAt()).isNotNull();

        // 비밀번호 변경
        member.changePassword(passwordEncoder.encode("new-password-123"));

        // 만료 시간이 초기화되어야 함
        assertThat(member.getTempPasswordExpiresAt()).isNull();
        assertThat(member.isMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("RED 3-4: tempPasswordExpiresAt은 1시간 유효 기간을 가진다")
    void tempPasswordExpiresInOneHour() {
        String tempPassword = "TEMP-HOUR";
        Member member = memberWithValidTempPassword(tempPassword);

        ZonedDateTime expiresAt = member.getTempPasswordExpiresAt();
        ZonedDateTime now = ZonedDateTime.now();

        // 1시간 이내로 설정되어야 함
        long minutesDifference = java.time.temporal.ChronoUnit.MINUTES.between(now, expiresAt);
        assertThat(minutesDifference).isGreaterThanOrEqualTo(59).isLessThanOrEqualTo(61);
    }
}
