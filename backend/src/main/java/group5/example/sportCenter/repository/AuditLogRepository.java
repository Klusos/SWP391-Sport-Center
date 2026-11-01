package group5.example.sportCenter.repository;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

import group5.example.sportCenter.entity.AuditLog;

public interface AuditLogRepository extends Repository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog>,
        AuditLogInsertRepository {
    long count();
    List<AuditLog> findAll();
    boolean existsByActorUserId(Integer actorUserId);
}
