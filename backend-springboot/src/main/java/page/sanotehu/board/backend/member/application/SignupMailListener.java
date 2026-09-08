package page.sanotehu.board.backend.member.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class SignupMailListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignupCompleted(SignupCompleted event) {
        log.info("Signup verification email event received; mail transport is not configured");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailVerificationRequested(EmailVerificationRequested event) {
        log.info("Email verification resend event received; mail transport is not configured");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        log.info("Password reset email event received; mail transport is not configured");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUsernameRecoveryRequested(UsernameRecoveryRequested event) {
        log.info("Username recovery email event received; mail transport is not configured");
    }
}