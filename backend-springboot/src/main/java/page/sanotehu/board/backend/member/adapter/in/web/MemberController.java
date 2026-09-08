package page.sanotehu.board.backend.member.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.member.adapter.in.web.dto.EmailVerificationResendCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetChangeCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetRequestCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.SignupCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.UsernameRecoveryCommand;
import page.sanotehu.board.backend.member.application.SignupService;
import page.sanotehu.board.backend.member.application.UsernameRecoveryService;
import page.sanotehu.board.backend.member.application.VerificationService;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final SignupService signupService;
    private final UsernameRecoveryService usernameRecoveryService;
    private final VerificationService verificationService;
    private final MemberRepository memberRepository;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> signup(@RequestBody @Valid SignupCommand cmd, HttpServletRequest request) {
        Long id = signupService.signup(cmd, request.getRemoteAddr());
        return Map.of("id", id);
    }

    @GetMapping("/verify-email")
    public Map<String, String> verifyEmail(@RequestParam String token) {
        verificationService.verifyEmail(token);
        return Map.of("status", "verified");
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> resendEmailVerification(
            @RequestBody @Valid EmailVerificationResendCommand command) {
        verificationService.resendEmailVerification(command.getEmail());
        return Map.of("status", "sent");
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestPasswordReset(
            @RequestBody @Valid PasswordResetRequestCommand command,
            HttpServletRequest request) {
        verificationService.requestPasswordReset(command, request.getRemoteAddr());
        return Map.of("status", "requested");
    }

    @PostMapping("/password-reset/change")
    public Map<String, String> changePassword(
            @RequestBody @Valid PasswordResetChangeCommand command) {
        verificationService.changePassword(command);
        return Map.of("status", "changed");
    }
    
    @GetMapping("/username-availability")
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        boolean exists = memberRepository.findByUsername(username).isPresent();
        return Map.of("available", !exists);
    }

    @GetMapping("/nickname-availability")
    public Map<String, Boolean> checkNickname(@RequestParam String nickname) {
        boolean exists = nickname != null && !nickname.isBlank()
                && memberRepository.findByNickname(nickname).isPresent();
        return Map.of("available", !exists);
    }

    @PostMapping("/username-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recoverUsername(
            @RequestBody @Valid UsernameRecoveryCommand command) {
        usernameRecoveryService.requestRecovery(command);
        return Map.of("message", "가입 이메일로 아이디 안내를 전송했습니다.");
    }
}