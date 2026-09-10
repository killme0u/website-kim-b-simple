package page.sanotehu.board.backend.member.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordCommand {

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
