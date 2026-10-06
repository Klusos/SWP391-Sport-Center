package group5.example.sportCenter.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MembershipResponse {

    private Integer id;

    private Integer memberId;
    private String memberName;

    private Integer planId;
    private String planName;

    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoRenew;
    private String status;
}