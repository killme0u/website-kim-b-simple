package page.sanotehu.board.backend.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByTokenHash(String tokenHash);
    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash, String purpose);
}