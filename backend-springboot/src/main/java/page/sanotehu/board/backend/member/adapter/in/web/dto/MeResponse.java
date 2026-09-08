package page.sanotehu.board.backend.member.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRole;
import page.sanotehu.board.backend.member.domain.MemberStatus;

@Data
@Builder
public class MeResponse {
    private Long id;
    private String username;
    private String name;
    private String email;
    private MemberStatus status;
    private MemberRole role;
    private boolean mustChangePassword;

    public static MeResponse from(Member m) {
        return MeResponse.builder()
                .id(m.getId())
                .username(m.getUsername())
                .name(m.getName())
                .email(m.getEmail())
                .status(m.getStatus())
                .role(m.getRole())
                .mustChangePassword(m.isMustChangePassword())
                .build();
    }
}