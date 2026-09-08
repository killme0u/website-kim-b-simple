package page.sanotehu.board.backend.member.application;

public record EmailVerificationRequested(String email, String rawToken) {
}
