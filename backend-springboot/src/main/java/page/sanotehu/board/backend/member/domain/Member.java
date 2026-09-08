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

    public static Member pending(String username, String passwordHash, String name,
                                 String nickname, String email, String phone) {
        Member member = new Member();
        member.username = username;
        member.passwordHash = passwordHash;
        member.name = name;
        member.nickname = nickname;
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
}