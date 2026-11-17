package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "reminder_jobs")
public class ReminderJob {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "assignment_id")
    public UUID assignmentId;
    @Column(name = "rule_id")
    public UUID ruleId;
    @Column(name = "scheduled_at")
    public Instant scheduledAt;
    @Column(name = "channel")
    public String channel;
    @Column(name = "status")
    public String status;
    @Column(name = "attempts")
    public Integer attempts;
    @Column(name = "next_attempt_at")
    public Instant nextAttemptAt;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
