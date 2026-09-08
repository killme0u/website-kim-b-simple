package page.sanotehu.board.backend.member.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.member.adapter.in.web.dto.MeResponse;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AuthenticationRequiredException("Not authenticated");
        }
        return MeResponse.from(userDetails.getMember());
    }
}