package group5.example.sportCenter.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import group5.example.sportCenter.entity.AuditLog;

class AuditLogInsertRepositoryImpl implements AuditLogInsertRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AuditLog insert(AuditLog entity) {
        entityManager.persist(entity);
        return entity;
    }
}
