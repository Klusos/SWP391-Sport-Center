package group5.example.sportCenter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SportResponse {

    private Integer id;
    private String name;
    private String description;
    private String status;
}