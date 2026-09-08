package page.sanotehu.board.backend.member.application;

public record PasswordResetRequested(String email, String rawToken) {
}
