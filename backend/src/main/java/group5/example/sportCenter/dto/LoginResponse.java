package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private Integer expiresIn;

    private Integer userId;
    private String name;
    private String email;
    private String role;
}