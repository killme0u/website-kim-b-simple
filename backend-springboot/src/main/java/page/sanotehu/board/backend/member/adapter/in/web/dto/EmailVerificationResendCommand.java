package page.sanotehu.board.backend.member.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationResendCommand {

    @NotBlank
    @Email
    private String email;
}
