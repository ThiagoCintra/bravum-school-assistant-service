package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "assignments")
public class Assignment {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "communication_id")
    public UUID communicationId;
    @Column(name = "source_item_key")
    public String sourceItemKey;
    @Column(name = "student_id")
    public UUID studentId;
    @Column(name = "student_context_id")
    public UUID studentContextId;
    @Column(name = "classification")
    public String classification;
    @Column(name = "title")
    public String title;
    @Column(name = "description")
    public String description;
    @Column(name = "due_at")
    public Instant dueAt;
    @Column(name = "due_date_source")
    public String dueDateSource;
    @Column(name = "confidence")
    public java.math.BigDecimal confidence;
    @Column(name = "requires_review")
    public Boolean requiresReview;
    @Column(name = "status")
    public String status;
    @Column(name = "completed_at")
    public Instant completedAt;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
