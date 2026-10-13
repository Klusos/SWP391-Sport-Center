package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.ConsultationRequest;
import group5.example.sportCenter.entity.ConsultationStatus;

import jakarta.persistence.LockModeType;

public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Integer> {

    List<ConsultationRequest> findByMemberIdOrderByCreatedAtDescIdDesc(Integer memberId);

    Page<ConsultationRequest> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<ConsultationRequest> findByStatusOrderByCreatedAtDescIdDesc(
            ConsultationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ConsultationRequest request where request.id = :id")
    Optional<ConsultationRequest> findByIdForUpdate(@Param("id") Integer id);
}
