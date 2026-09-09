package page.sanotehu.board.backend.member.adapter.out.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /** 발신자 주소. */
    private String from = "no-reply@localhost";

    /** 메일 본문에 넣을 링크의 기준 주소. 리버스 프록시 뒤에 있다면 외부에서 보이는 주소로 설정한다. */
    private String baseUrl = "http://localhost:8080";
}
