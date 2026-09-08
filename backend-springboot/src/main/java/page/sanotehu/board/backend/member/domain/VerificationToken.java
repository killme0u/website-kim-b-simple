package page.sanotehu.board.backend.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.ZonedDateTime;

@Entity
@Table(name = "verification_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime usedAt;

    public static VerificationToken emailVerify(Member member, String tokenHash, Duration validFor) {
        return create(member, tokenHash, "EMAIL_VERIFICATION", validFor);
    }

    public static VerificationToken passwordReset(Member member, String tokenHash, Duration validFor) {
        return create(member, tokenHash, "PASSWORD_RESET", validFor);
    }

    private static VerificationToken create(Member member, String tokenHash, String purpose, Duration validFor) {
        VerificationToken token = new VerificationToken();
        token.member = member;
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.expiresAt = ZonedDateTime.now().plus(validFor);
        return token;
    }

    public boolean isUsable(String expectedPurpose) {
        return expectedPurpose.equals(this.purpose)
                && this.usedAt == null
                && ZonedDateTime.now().isBefore(this.expiresAt);
    }

    public void markUsed() {
        if (this.usedAt != null) {
            throw new IllegalArgumentException("토큰이 이미 사용되었습니다.");
        }
        this.usedAt = ZonedDateTime.now();
    }
}