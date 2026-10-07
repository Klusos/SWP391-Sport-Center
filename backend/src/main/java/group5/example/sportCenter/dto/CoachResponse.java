package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CoachResponse {

    private Integer id;
    private Integer userId;
    private String userName;
    private String email;
    private String status;
}