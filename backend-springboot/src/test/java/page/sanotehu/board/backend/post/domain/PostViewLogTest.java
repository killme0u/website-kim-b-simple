package page.sanotehu.board.backend.post.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostViewLog 테스트")
class PostViewLogTest {

    @Test
    @DisplayName("회원 조회 로그를 생성할 수 있다")
    void createMemberViewLog() {
        PostViewLog log = new PostViewLog(1L, 100L, LocalDate.now());

        assertThat(log.getPostId()).isEqualTo(1L);
        assertThat(log.getMemberId()).isEqualTo(100L);
        assertThat(log.getIpAddress()).isNull();
        assertThat(log.getViewedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("비회원 조회 로그를 생성할 수 있다")
    void createGuestViewLog() {
        PostViewLog log = new PostViewLog(1L, "192.168.1.100", LocalDate.now());

        assertThat(log.getPostId()).isEqualTo(1L);
        assertThat(log.getMemberId()).isNull();
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(log.getViewedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("IPv6 주소를 저장할 수 있다")
    void storeIPv6Address() {
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        PostViewLog log = new PostViewLog(1L, ipv6, LocalDate.now());

        assertThat(log.getIpAddress()).isEqualTo(ipv6);
    }
}
