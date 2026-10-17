package group5.example.sportCenter.dto;

public record PublicFacilityResponse(
        Integer id,
        Integer sportId,
        String sportName,
        String name,
        String type,
        String imageUrl) {}
