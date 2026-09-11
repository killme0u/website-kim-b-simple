package page.sanotehu.board.backend.member.adapter.in.web.dto;

import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRole;
import page.sanotehu.board.backend.member.domain.MemberStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeResponse {
    private Long id;
    private String username;
    private String name;
    private String nickname;
    private String email;
    private MemberStatus status;
    private MemberRole role;
    private boolean mustChangePassword;

    public static MeResponse from(Member m) {
        return MeResponse.builder()
                .id(m.getId())
                .username(m.getUsername())
                .name(m.getName())
                .nickname(m.getNickname())
                .email(m.getEmail())
                .status(m.getStatus())
                .role(m.getRole())
                .mustChangePassword(m.isMustChangePassword())
                .build();
    }
}