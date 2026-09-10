package page.sanotehu.board.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.HttpStatusAccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // "로그인 유지" 선결 결정 #4의 최종 답: 세션 쿠키는 그대로 두고, 체크했을 때만 별도 remember-me 토큰을 추가 발급한다.
    private static final int REMEMBER_ME_VALIDITY_SECONDS = (int) Duration.ofDays(30).toSeconds();
    // RememberMeAuthenticationProvider가 토큰 발급 주체를 확인하는 내부 키. 쿠키 값에는 노출되지 않는다.
    private static final String REMEMBER_ME_KEY = "board-remember-me";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, PersistentTokenRepository persistentTokenRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 익명 열람 가능한 읽기 API. 게시판별 로그인 필요 여부는 Board 정책이 판정한다.
                .requestMatchers(HttpMethod.GET, "/api/boards/**", "/api/posts/**", "/api/files/**").permitAll()
                .requestMatchers("/api/auth/**",
                                 "/api/members/signup",
                                 "/api/members/username-availability",
                                 "/api/members/nickname-availability",
                                 "/api/members/username-recovery",
                                 "/api/members/verify-email/**",
                                 "/api/members/password-reset/**").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/boards/*/posts", "/api/files").permitAll()
                .requestMatchers(HttpMethod.PUT,    "/api/posts/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/posts/*").permitAll()
                .requestMatchers("/api/**").authenticated()
                // SPA 셸(index.html, 정적 자산, 클라이언트 라우트)은 비로그인 사용자도 받아야 한다.
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(new JsonAuthenticationSuccessHandler())
                .failureHandler(new JsonAuthenticationFailureHandler())
            )
            .rememberMe(remember -> remember
                .tokenRepository(persistentTokenRepository)
                .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS)
                .key(REMEMBER_ME_KEY)
            )
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            )
            // spa() = CookieCsrfTokenRepository.withHttpOnlyFalse() + SpaCsrfTokenRequestHandler.
            // 매 요청마다 XSRF-TOKEN 쿠키를 실제로 내려주고, 헤더로 들어온 원본 토큰을 그대로 검증한다.
            // (기본 XorCsrfTokenRequestAttributeHandler는 마스킹된 토큰을 기대하므로 쿠키 값을 그대로 보내는 SPA에서 403이 난다.)
            .csrf(csrf -> csrf
                .spa()
                .ignoringRequestMatchers("/api/auth/login", "/api/members/signup")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.FORBIDDEN))
            )
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .sessionConcurrency(concurrency -> concurrency.maximumSessions(1))
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        repository.setCreateTableOnStartup(false); // 테이블은 V6 마이그레이션이 만든다
        return repository;
    }
}
