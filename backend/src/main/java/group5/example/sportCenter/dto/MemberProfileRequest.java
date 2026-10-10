package group5.example.sportCenter.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

public class MemberProfileRequest {

    @DecimalMin(value = "50.00", message = "Height must be at least 50 cm")
    @DecimalMax(value = "250.00", message = "Height must not exceed 250 cm")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal height;

    @DecimalMin(value = "10.00", message = "Weight must be at least 10 kg")
    @DecimalMax(value = "500.00", message = "Weight must not exceed 500 kg")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal weight;

    @Size(max = 1000)
    private String goal;

    @JsonIgnore
    private boolean goalProvided;

    public BigDecimal height() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public BigDecimal weight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String goal() {
        return goal;
    }

    @JsonSetter(value = "goal", nulls = Nulls.SET)
    public void setGoal(String goal) {
        this.goal = goal;
        this.goalProvided = true;
    }

    @JsonIgnore
    public boolean isGoalProvided() {
        return goalProvided;
    }
}
