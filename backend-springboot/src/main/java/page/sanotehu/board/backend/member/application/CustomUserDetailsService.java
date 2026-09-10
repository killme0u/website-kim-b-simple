package page.sanotehu.board.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRepository;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByUsername(username)
                .map(this::handleTempPasswordAndLoadUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private CustomUserDetails handleTempPasswordAndLoadUserDetails(Member member) {
        // 임시 비밀번호가 유효하면 must_change_password = true 설정
        ZonedDateTime tempPasswordExpiresAt = member.getTempPasswordExpiresAt();
        if (tempPasswordExpiresAt != null && tempPasswordExpiresAt.isAfter(ZonedDateTime.now())) {
            member.setMustChangePassword(true);
            memberRepository.save(member);
        }
        return new CustomUserDetails(member);
    }
}