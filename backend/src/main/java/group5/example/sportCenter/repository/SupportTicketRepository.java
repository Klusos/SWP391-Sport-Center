package group5.example.sportCenter.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import group5.example.sportCenter.entity.SupportTicket;
import jakarta.persistence.LockModeType;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Integer>, JpaSpecificationExecutor<SupportTicket> {
    @EntityGraph(attributePaths = {"member", "assignedStaff"})
    Page<SupportTicket> findByMemberId(Integer memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "assignedStaff"})
    Page<SupportTicket> findAll(Specification<SupportTicket> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from SupportTicket ticket where ticket.id = :id")
    java.util.Optional<SupportTicket> findByIdForUpdate(@Param("id") Integer id);
}
