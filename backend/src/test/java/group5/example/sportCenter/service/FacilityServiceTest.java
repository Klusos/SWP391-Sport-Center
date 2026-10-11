package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.FacilityResponse;
import group5.example.sportCenter.dto.UpdateFacilityRequest;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;

@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-11T12:00:00");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;

    private FacilityService service;
    private Facility facility;

    @BeforeEach
    void setUp() {
        service = new FacilityService(facilityRepository, sportRepository, classSessionRepository, FIXED_CLOCK);
        Sport sport = new Sport();
        sport.setId(4);
        sport.setName("Tennis");

        facility = new Facility();
        facility.setId(6);
        facility.setSport(sport);
        facility.setName("Court A");
        facility.setType("COURT");
        facility.setStatus("ACTIVE");
    }

    @Test
    void normalizesActiveInputToUppercase() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(facilityRepository.save(facility)).thenReturn(facility);

        FacilityResponse response = service.updateFacility(6, request(" active "));

        assertEquals("ACTIVE", facility.getStatus());
        assertEquals("ACTIVE", response.getStatus());
        verify(classSessionRepository, never())
                .existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW);
    }

    @Test
    void allowsActiveToInactiveWhenThereIsNoInProgressOrUpcomingScheduledSession() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW))
                .thenReturn(false);
        when(facilityRepository.save(facility)).thenReturn(facility);

        FacilityResponse response = service.updateFacility(6, request("inactive"));

        assertEquals("INACTIVE", facility.getStatus());
        assertEquals("INACTIVE", response.getStatus());
        verify(classSessionRepository).existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW);
    }

    @Test
    void rejectsActiveToInactiveWhenScheduledSessionIsInProgress() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateFacility(6, request("INACTIVE")));

        assertTrue(exception.getMessage().contains("in-progress or upcoming SCHEDULED ClassSession"));
        assertEquals("ACTIVE", facility.getStatus());
        verify(facilityRepository, never()).save(any(Facility.class));
    }

    @Test
    void rejectsActiveToInactiveWhenFutureScheduledSessionExists() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateFacility(6, request("INACTIVE")));

        assertEquals("ACTIVE", facility.getStatus());
        verify(facilityRepository, never()).save(any(Facility.class));
    }

    @Test
    void allowsDeactivationWhenScheduledSessionHasEnded() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW))
                .thenReturn(false);
        when(facilityRepository.save(facility)).thenReturn(facility);

        FacilityResponse response = service.updateFacility(6, request("INACTIVE"));

        assertEquals("INACTIVE", response.getStatus());
    }

    @Test
    void allowsDeactivationWhenScheduledSessionEndsExactlyNow() {
        when(facilityRepository.findById(6)).thenReturn(Optional.of(facility));
        when(classSessionRepository.existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW))
                .thenReturn(false);
        when(facilityRepository.save(facility)).thenReturn(facility);

        FacilityResponse response = service.updateFacility(6, request("INACTIVE"));

        assertEquals("INACTIVE", response.getStatus());
        verify(classSessionRepository).existsByFacilityIdAndStatusAndEndTimeAfter(6, "SCHEDULED", NOW);
    }

    @Test
    void rejectsNullBlankAndUnknownStatusesAsBadRequest() {
        for (String invalidStatus : new String[] {null, "", "   ", "BANANA"}) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.updateFacility(6, request(invalidStatus)));
            assertEquals(400, exception.getStatusCode().value());
        }

        verify(facilityRepository, never()).findById(6);
    }

    private UpdateFacilityRequest request(String status) {
        UpdateFacilityRequest request = new UpdateFacilityRequest();
        request.setName("Court A updated");
        request.setType("COURT");
        request.setStatus(status);
        return request;
    }
}
