package page.sanotehu.board.backend.member.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UsernameRecoveryCommand {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String captchaToken;
}
