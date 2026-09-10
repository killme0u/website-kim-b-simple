package page.sanotehu.board.backend.member.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;
import page.sanotehu.board.backend.member.adapter.in.web.dto.ChangePasswordCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.EmailVerificationResendCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetChangeCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.PasswordResetRequestCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.SignupCommand;
import page.sanotehu.board.backend.member.adapter.in.web.dto.UsernameRecoveryCommand;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.member.application.SignupService;
import page.sanotehu.board.backend.member.application.UsernameRecoveryService;
import page.sanotehu.board.backend.member.application.VerificationService;
import page.sanotehu.board.backend.member.domain.Member;
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

    @PostMapping("/password-reset/issue-temp-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> issueTempPassword(
            @RequestBody @Valid PasswordResetRequestCommand command,
            HttpServletRequest request) {
        verificationService.issueTempPassword(command, request.getRemoteAddr());
        return Map.of("status", "issued");
    }

    @PostMapping("/password-reset/change")
    public Map<String, String> changePassword(
            @RequestBody @Valid PasswordResetChangeCommand command) {
        verificationService.changePassword(command);
        return Map.of("status", "changed");
    }

    @PostMapping("/change-password")
    public Map<String, String> changePasswordByUser(
            @RequestBody @Valid ChangePasswordCommand command,
            @AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");
        verificationService.changePasswordByUser(user.getId(), command);
        return Map.of("status", "changed");
    }
    
    @GetMapping("/username-availability")
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        boolean exists = memberRepository.findByUsername(username).isPresent();
        return Map.of("available", !exists);
    }

    /**
     * 닉네임 중복 확인. 아이디 중복 확인과 마찬가지로 UX 편의 기능이며, 실제 방어선은
     * {@code ux_member_nickname} UNIQUE 인덱스와 409 변환이다(PRD 2.4).
     * 저장 시점과 같은 정규화를 거쳐 조회해야 " 홍길동"이 "사용 가능"으로 보인 뒤 가입에서 409가 나는 일이 없다.
     * 정규화 결과가 비면 닉네임을 쓰지 않겠다는 뜻이라 충돌 대상도 없으므로 `available: true`다.
     */
    @GetMapping("/nickname-availability")
    public Map<String, Boolean> checkNickname(@RequestParam String nickname) {
        String normalized = Member.normalizeNickname(nickname);
        boolean exists = normalized != null && memberRepository.findByNickname(normalized).isPresent();
        return Map.of("available", !exists);
    }

    @PostMapping("/username-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recoverUsername(
            @RequestBody @Valid UsernameRecoveryCommand command,
            HttpServletRequest request) {
        usernameRecoveryService.requestRecovery(command, request.getRemoteAddr());
        return Map.of("message", "가입 이메일로 아이디 안내를 전송했습니다.");
    }
}