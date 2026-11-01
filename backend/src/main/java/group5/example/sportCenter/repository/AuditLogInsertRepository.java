package group5.example.sportCenter.repository;

import group5.example.sportCenter.entity.AuditLog;

interface AuditLogInsertRepository {
    AuditLog insert(AuditLog entity);
}
