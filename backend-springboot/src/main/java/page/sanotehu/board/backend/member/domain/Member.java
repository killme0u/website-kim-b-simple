package page.sanotehu.board.backend.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import page.sanotehu.board.backend.common.BaseTimeEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(length = 30, unique = true)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.USER;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "temp_password_expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime tempPasswordExpiresAt;

    /**
     * 닉네임 정규화 규칙. 앞뒤 공백을 지우고, 빈 값은 "닉네임 없음"(null)으로 본다.
     * 저장 경로와 중복 확인 조회가 같은 값을 보게 하려면 규칙이 도메인 한 곳에만 있어야 한다.
     * (규칙이 갈라지면 " 홍길동"이 중복 확인은 통과하고 저장 시 UNIQUE 위반으로 409가 난다.)
     */
    public static String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String trimmed = nickname.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static Member pending(String username, String passwordHash, String name,
                                 String nickname, String email, String phone) {
        Member member = new Member();
        member.username = username;
        member.passwordHash = passwordHash;
        member.name = name;
        member.nickname = normalizeNickname(nickname);
        member.email = email;
        member.phone = phone;
        member.status = MemberStatus.PENDING;
        member.role = MemberRole.USER;
        return member;
    }

    public static Member pending(String username, String passwordHash, String name, String email, String phone) {
        return pending(username, passwordHash, name, null, email, phone);
    }

    public void verifyEmail() {
        if (this.status == MemberStatus.PENDING) {
            this.status = MemberStatus.ACTIVE;
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        this.tempPasswordExpiresAt = null;
    }

    public void setTempPassword(String passwordHash, ZonedDateTime expiresAt) {
        this.passwordHash = passwordHash;
        this.tempPasswordExpiresAt = expiresAt;
    }

    public void setTempPasswordExpiresAt(ZonedDateTime expiresAt) {
        this.tempPasswordExpiresAt = expiresAt;
    }

    public boolean isTempPasswordExpired() {
        return tempPasswordExpiresAt != null && tempPasswordExpiresAt.isBefore(ZonedDateTime.now());
    }

    public void setMustChangePassword(boolean flag) {
        this.mustChangePassword = flag;
    }
}