package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import group5.example.sportCenter.dto.CreateSupportTicketMessageRequest;
import group5.example.sportCenter.dto.CreateSupportTicketRequest;
import group5.example.sportCenter.dto.SupportTicketDetailResponse;
import group5.example.sportCenter.dto.SupportTicketMessageResponse;
import group5.example.sportCenter.dto.SupportTicketSummaryResponse;
import group5.example.sportCenter.dto.UpdateSupportTicketStatusRequest;
import group5.example.sportCenter.entity.SupportTicket;
import group5.example.sportCenter.entity.SupportTicketCategory;
import group5.example.sportCenter.entity.SupportTicketMessage;
import group5.example.sportCenter.entity.SupportTicketStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.SupportTicketMessageRepository;
import group5.example.sportCenter.repository.SupportTicketRepository;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class SupportTicketService {
    private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository messages;
    private final AssessmentAccess access;

    public SupportTicketService(SupportTicketRepository tickets, SupportTicketMessageRepository messages,
            AssessmentAccess access) {
        this.tickets = tickets;
        this.messages = messages;
        this.access = access;
    }

    @Transactional
    public SupportTicketDetailResponse create(CreateSupportTicketRequest request, Jwt jwt) {
        User member = access.actor(jwt);
        access.requireMember(member);
        SupportTicket ticket = new SupportTicket();
        ticket.setMember(member);
        ticket.setSubject(request.subject().trim());
        ticket.setCategory(request.category());
        ticket.setDescription(request.description().trim());
        ticket.setStatus(SupportTicketStatus.OPEN);
        return detail(tickets.saveAndFlush(ticket));
    }

    public Page<SupportTicketSummaryResponse> mine(int page, int size, Jwt jwt) {
        User member = access.actor(jwt);
        access.requireMember(member);
        return tickets.findByMemberId(member.getId(), pageable(page, size))
                .map(this::summary);
    }

    public Page<SupportTicketSummaryResponse> staffList(String status, String category, String search,
            int page, int size, Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        SupportTicketStatus selectedStatus = parseStatus(status);
        SupportTicketCategory selectedCategory = parseCategory(category);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (query.length() > 100) throw AssessmentAccess.error(HttpStatus.BAD_REQUEST, "search is limited to 100 characters");
        Specification<SupportTicket> specification = (root, criteriaQuery, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            if (selectedStatus != null) predicates.add(cb.equal(root.get("status"), selectedStatus));
            if (selectedCategory != null) predicates.add(cb.equal(root.get("category"), selectedCategory));
            if (!query.isBlank()) {
                String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                        cb.like(cb.lower(root.join("member").get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return tickets.findAll(specification, pageable(page, size)).map(this::summary);
    }

    public SupportTicketDetailResponse get(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        SupportTicket ticket = find(id);
        authorizeRead(actor, ticket);
        return detail(ticket);
    }

    @Transactional
    public SupportTicketDetailResponse reply(Integer id, CreateSupportTicketMessageRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        SupportTicket ticket = locked(id);
        authorizeRead(actor, ticket);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw AssessmentAccess.error(HttpStatus.CONFLICT, "Closed tickets are read-only");
        }
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            ticket.setStatus(SupportTicketStatus.OPEN);
            ticket.setResolvedAt(null);
        } else if (ticket.getStatus() == SupportTicketStatus.OPEN && isStaff(actor)) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        }
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setSender(actor);
        message.setMessage(request.message().trim());
        messages.save(message);
        tickets.saveAndFlush(ticket);
        return detail(ticket);
    }

    @Transactional
    public SupportTicketDetailResponse updateStatus(Integer id, UpdateSupportTicketStatusRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireStaff(actor);
        SupportTicket ticket = locked(id);
        SupportTicketStatus current = ticket.getStatus();
        SupportTicketStatus next = request.status();
        boolean valid = switch (current) {
            case OPEN -> next == SupportTicketStatus.IN_PROGRESS || next == SupportTicketStatus.RESOLVED;
            case IN_PROGRESS -> next == SupportTicketStatus.RESOLVED;
            case RESOLVED -> next == SupportTicketStatus.CLOSED || next == SupportTicketStatus.OPEN;
            case CLOSED -> false;
        };
        if (!valid) throw AssessmentAccess.error(HttpStatus.CONFLICT,
                "Invalid ticket status transition: " + current + " -> " + next);
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(next);
        if (next == SupportTicketStatus.RESOLVED) ticket.setResolvedAt(now);
        if (next == SupportTicketStatus.CLOSED) ticket.setClosedAt(now);
        if (next == SupportTicketStatus.OPEN) ticket.setResolvedAt(null);
        return detail(tickets.saveAndFlush(ticket));
    }

    @Transactional
    public SupportTicketDetailResponse assignToMe(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireStaff(actor);
        SupportTicket ticket = locked(id);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw AssessmentAccess.error(HttpStatus.CONFLICT, "Closed tickets cannot be assigned");
        }
        if (ticket.getAssignedStaff() != null && !ticket.getAssignedStaff().getId().equals(actor.getId())) {
            throw AssessmentAccess.error(HttpStatus.CONFLICT, "Ticket is assigned to another staff member");
        }
        ticket.setAssignedStaff(actor);
        return detail(tickets.saveAndFlush(ticket));
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw AssessmentAccess.error(HttpStatus.BAD_REQUEST, "page must be nonnegative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private SupportTicketStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return SupportTicketStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw AssessmentAccess.error(HttpStatus.BAD_REQUEST, "Invalid support ticket status"); }
    }

    private SupportTicketCategory parseCategory(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return SupportTicketCategory.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw AssessmentAccess.error(HttpStatus.BAD_REQUEST, "Invalid support ticket category"); }
    }

    private void authorizeRead(User actor, SupportTicket ticket) {
        if (isStaff(actor)) return;
        access.requireMember(actor);
        if (!ticket.getMember().getId().equals(actor.getId())) {
            throw AssessmentAccess.error(HttpStatus.FORBIDDEN, "Ticket belongs to another member");
        }
    }

    private boolean isStaff(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "RECEPTIONIST".equalsIgnoreCase(user.getRole());
    }

    private SupportTicket find(Integer id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + id));
    }

    private SupportTicket locked(Integer id) {
        return tickets.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + id));
    }

    private SupportTicketSummaryResponse summary(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(ticket.getId(), ticket.getMember().getId(), ticket.getMember().getName(),
                ticket.getSubject(), ticket.getCategory().name(), ticket.getStatus().name(),
                ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId(),
                ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getName(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private SupportTicketDetailResponse detail(SupportTicket ticket) {
        var thread = messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                .map(message -> new SupportTicketMessageResponse(message.getId(), message.getSender().getId(),
                        message.getSender().getName(), message.getSender().getRole(), message.getMessage(), message.getCreatedAt()))
                .toList();
        return new SupportTicketDetailResponse(ticket.getId(), ticket.getMember().getId(), ticket.getMember().getName(),
                ticket.getSubject(), ticket.getCategory().name(), ticket.getDescription(), ticket.getStatus().name(),
                ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId(),
                ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getName(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getResolvedAt(), ticket.getClosedAt(), thread);
    }
}
