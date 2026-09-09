package page.sanotehu.board.backend.member.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import page.sanotehu.board.backend.config.SecurityConfig;
import page.sanotehu.board.backend.member.application.SignupService;
import page.sanotehu.board.backend.member.application.UsernameRecoveryService;
import page.sanotehu.board.backend.member.application.VerificationService;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    private static final String NICKNAME_URI = "/api/members/nickname-availability";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SignupService signupService;
    @MockitoBean
    private UsernameRecoveryService usernameRecoveryService;
    @MockitoBean
    private VerificationService verificationService;
    @MockitoBean
    private MemberRepository memberRepository;

    private void assertAvailability(String nickname, boolean expected) {
        assertThat(mvc.get().uri(NICKNAME_URI).param("nickname", nickname))
                .hasStatusOk()
                .bodyJson().extractingPath("$.available").isEqualTo(expected);
    }

    @Test
    @DisplayName("이미 쓰는 닉네임은 available=false로 응답한다")
    void reportsTakenNickname() {
        given(memberRepository.findByNickname("단팥빵")).willReturn(Optional.of(
                Member.pending("hong", "hash", "홍길동", "단팥빵", "hong@example.com", "01012345678")));

        assertAvailability("단팥빵", false);
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 닉네임도 중복으로 판정한다 - 저장 시 trim되므로 그대로 두면 가입에서 409가 난다")
    void trimsNicknameBeforeLookup() {
        given(memberRepository.findByNickname("단팥빵")).willReturn(Optional.of(
                Member.pending("hong", "hash", "홍길동", "단팥빵", "hong@example.com", "01012345678")));

        assertAvailability("  단팥빵  ", false);
    }

    @Test
    @DisplayName("비어 있는 닉네임은 조회 없이 available=true - 닉네임은 선택 항목이라 충돌 대상이 없다")
    void treatsBlankNicknameAsAvailable() {
        assertAvailability("   ", true);

        verify(memberRepository, never()).findByNickname(any());
    }

    @Test
    @DisplayName("아직 쓰지 않는 닉네임은 available=true로 응답한다")
    void reportsFreeNickname() {
        given(memberRepository.findByNickname("단팥빵")).willReturn(Optional.empty());

        assertAvailability("단팥빵", true);
    }
}
