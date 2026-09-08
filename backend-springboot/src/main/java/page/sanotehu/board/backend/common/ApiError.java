package page.sanotehu.board.backend.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private String code;
    private String message;

    public static ApiError of(String code, String message) {
        return new ApiError(code, message);
    }
}