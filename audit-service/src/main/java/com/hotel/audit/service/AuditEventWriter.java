package com.hotel.audit.service;

import com.hotel.audit.entity.AuditEvent;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts audit events with an explicit INSERT.
 *
 * <p>{@code JpaRepository.save} would merge (and therefore overwrite) an event whose
 * assigned id already exists; persisting instead lets the primary key on
 * {@code event_id} reject duplicates so concurrent deliveries of the same event
 * cannot both land.
 *
 * <p>Runs in its own transaction so a constraint violation only rolls back the failed
 * insert and the caller can still read the winning row.
 */
@Repository
class AuditEventWriter {

    private final EntityManager entityManager;

    AuditEventWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    void insert(AuditEvent event) {
        try {
            entityManager.persist(event);
            entityManager.flush();
        } catch (ConstraintViolationException e) {
            throw new DataIntegrityViolationException(e.getMessage(), e);
        }
    }
}
