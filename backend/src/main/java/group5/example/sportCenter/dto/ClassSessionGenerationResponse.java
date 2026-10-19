package group5.example.sportCenter.dto;

import java.util.List;

public record ClassSessionGenerationResponse(
        Integer courseClassId,
        int createdCount,
        List<ClassSessionResponse> sessions) { }
