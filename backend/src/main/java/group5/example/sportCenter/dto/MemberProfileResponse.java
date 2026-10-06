package group5.example.sportCenter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MemberProfileResponse {

    private Integer id;

    private Integer memberId;
    private String memberName;

    private BigDecimal height;
    private BigDecimal weight;
    private String goal;

    private LocalDateTime updatedAt;
}