package page.sanotehu.board.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
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
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(new JsonAuthenticationSuccessHandler())
                .failureHandler(new JsonAuthenticationFailureHandler())
            )
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, a) -> res.setStatus(204))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/login", "/api/members/signup", "/api/files") 
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> res.setStatus(401))
                .accessDeniedHandler((req, res, e) -> res.setStatus(403))
            )
            .sessionManagement(s -> s
                .sessionFixation(sf -> sf.changeSessionId())
                .maximumSessions(1)
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}