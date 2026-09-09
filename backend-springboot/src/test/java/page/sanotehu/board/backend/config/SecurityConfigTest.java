package page.sanotehu.board.backend.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import page.sanotehu.board.backend.member.adapter.in.web.AuthController;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("모든 응답에 XSRF-TOKEN 쿠키가 내려간다 - SPA가 토큰을 읽을 수 있어야 로그아웃이 가능하다")
    void writesCsrfCookieOnEveryResponse() {
        Cookie csrf = mvc.get().uri("/api/me").exchange().getResponse().getCookie(CSRF_COOKIE);

        assertThat(csrf).isNotNull();
        assertThat(csrf.getValue()).isNotBlank();
        assertThat(csrf.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("CSRF 토큰 없는 로그아웃은 403으로 거부된다")
    void logoutWithoutCsrfTokenIsForbidden() {
        assertThat(mvc.post().uri("/api/auth/logout"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("쿠키에서 읽은 원본 CSRF 토큰을 헤더로 보내면 로그아웃이 성공한다")
    void logoutWithCookieCsrfTokenSucceeds() {
        Cookie csrf = mvc.get().uri("/api/me").exchange().getResponse().getCookie(CSRF_COOKIE);
        assertThat(csrf).isNotNull();

        assertThat(mvc.post().uri("/api/auth/logout")
                .cookie(csrf)
                .header(CSRF_HEADER, csrf.getValue()))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("비로그인 상태에서 SPA 셸(정적 리소스)은 401이 아니어야 한다")
    void spaShellIsReachableAnonymously() {
        assertThat(mvc.get().uri("/login").exchange().getResponse().getStatus())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
