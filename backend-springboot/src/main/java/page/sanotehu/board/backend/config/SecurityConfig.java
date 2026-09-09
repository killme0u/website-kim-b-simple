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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
}
