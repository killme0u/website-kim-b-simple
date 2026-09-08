package page.sanotehu.board.backend.captcha.application;

public interface CaptchaVerifier {

    boolean verify(String token, String remoteAddress);
}
