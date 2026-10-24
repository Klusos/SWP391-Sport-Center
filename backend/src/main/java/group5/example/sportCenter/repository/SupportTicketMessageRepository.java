package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import group5.example.sportCenter.entity.SupportTicketMessage;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Integer> {
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAscIdAsc(Integer ticketId);
}
