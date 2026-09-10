package page.sanotehu.board.backend.member.application;

import lombok.Getter;

@Getter
public class TempPasswordIssued {
    private final String email;
    private final String tempPassword;

    public TempPasswordIssued(String email, String tempPassword) {
        this.email = email;
        this.tempPassword = tempPassword;
    }
}
