package page.sanotehu.board.backend.member.adapter.in.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.sanotehu.board.backend.member.domain.Member;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MeResponse 변환 테스트")
class MeResponseTest {

    @Test
    @DisplayName("Member를 MeResponse로 변환할 때 nickname을 포함해야 한다")
    void includesNicknameInConversion() {
        Member member = Member.pending(
                "testuser",
                "passwordHash",
                "테스트 사용자",
                "테스트닉네임",
                "test@example.com",
                "01012345678"
        );

        MeResponse response = MeResponse.from(member);

        assertThat(response)
                .extracting("username", "name", "email", "nickname")
                .containsExactly("testuser", "테스트 사용자", "test@example.com", "테스트닉네임");
    }

    @Test
    @DisplayName("닉네임이 없는 Member는 MeResponse에서도 nickname=null이어야 한다")
    void handlesNullNickname() {
        Member member = Member.pending(
                "testuser",
                "passwordHash",
                "테스트 사용자",
                "test@example.com",
                "01012345678"
        );

        MeResponse response = MeResponse.from(member);

        assertThat(response.getNickname()).isNull();
    }
}
