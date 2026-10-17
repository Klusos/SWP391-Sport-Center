package group5.example.sportCenter.dto;

import java.util.List;

public record PublicCoachResponse(Integer id, String name, String imageUrl, String bio,
                                  List<PublicCoachSport> sports) {
    public record PublicCoachSport(Integer sportId, String sportName, Integer experienceYears,
                                   String certification) {}
}
