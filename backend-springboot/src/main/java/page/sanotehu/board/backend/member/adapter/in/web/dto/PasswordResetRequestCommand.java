package page.sanotehu.board.backend.member.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequestCommand {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(max = 4096)
    private String captchaToken;
}
