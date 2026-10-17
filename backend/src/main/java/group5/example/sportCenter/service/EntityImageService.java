package group5.example.sportCenter.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Facility;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.FacilityRepository;
import group5.example.sportCenter.repository.SportRepository;

@Service
public class EntityImageService {
    private final LocalImageStorageService storage;
    private final SportRepository sports;
    private final CoachRepository coaches;
    private final FacilityRepository facilities;

    public EntityImageService(LocalImageStorageService storage, SportRepository sports,
            CoachRepository coaches, FacilityRepository facilities) {
        this.storage = storage;
        this.sports = sports;
        this.coaches = coaches;
        this.facilities = facilities;
    }

    @Transactional
    public void replaceSportImage(Integer id, MultipartFile file) {
        Sport sport = sports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sport not found with id: " + id));
        LocalImageStorageService.StoredImage stored = storage.store(file);
        replace(stored, sport.getImageUrl(), () -> { sport.setImageUrl(stored.imageUrl()); sports.saveAndFlush(sport); });
    }

    @Transactional
    public void replaceCoachImage(Integer id, MultipartFile file) {
        Coach coach = coaches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coach not found with id: " + id));
        LocalImageStorageService.StoredImage stored = storage.store(file);
        replace(stored, coach.getImageUrl(), () -> { coach.setImageUrl(stored.imageUrl()); coaches.saveAndFlush(coach); });
    }

    @Transactional
    public void replaceFacilityImage(Integer id, MultipartFile file) {
        Facility facility = facilities.findById(id).orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + id));
        LocalImageStorageService.StoredImage stored = storage.store(file);
        replace(stored, facility.getImageUrl(), () -> { facility.setImageUrl(stored.imageUrl()); facilities.saveAndFlush(facility); });
    }

    private void replace(LocalImageStorageService.StoredImage stored, String oldUrl, Runnable persist) {
        try {
            persist.run();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (!stored.imageUrl().equals(oldUrl)) storage.deleteStoredUrl(oldUrl);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) storage.deleteStoredUrl(stored.imageUrl());
                }
            });
        } catch (RuntimeException exception) {
            storage.deleteStoredUrl(stored.imageUrl());
            throw exception;
        }
    }
}
