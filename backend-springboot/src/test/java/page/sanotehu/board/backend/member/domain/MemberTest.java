package page.sanotehu.board.backend.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 닉네임 정규화는 저장 경로(`pending`)와 중복 확인 조회가 공유하는 규칙이다.
 * 두 경로가 다른 값을 보면 "사용 가능"으로 표시된 닉네임이 가입에서 409로 튕긴다.
 */
class MemberTest {

    private Member pendingWithNickname(String nickname) {
        return Member.pending("hong", "hash", "홍길동", nickname, "hong@example.com", "01012345678");
    }

    @Test
    @DisplayName("닉네임 앞뒤 공백은 저장 전에 제거된다")
    void trimsNicknameOnCreation() {
        assertThat(pendingWithNickname("  단팥빵  ").getNickname()).isEqualTo("단팥빵");
    }

    @Test
    @DisplayName("공백뿐인 닉네임은 닉네임 없음(null)으로 저장된다 - UNIQUE 인덱스가 NULL은 중복으로 보지 않는다")
    void treatsBlankNicknameAsAbsent() {
        assertThat(pendingWithNickname("   ").getNickname()).isNull();
        assertThat(pendingWithNickname("").getNickname()).isNull();
        assertThat(pendingWithNickname(null).getNickname()).isNull();
    }

    @Test
    @DisplayName("정규화 규칙은 중복 확인 쪽에서 재사용할 수 있게 공개돼 있다")
    void exposesNormalizationRule() {
        assertThat(Member.normalizeNickname("  단팥빵  ")).isEqualTo("단팥빵");
        assertThat(Member.normalizeNickname("  ")).isNull();
        assertThat(Member.normalizeNickname(null)).isNull();
    }
}
