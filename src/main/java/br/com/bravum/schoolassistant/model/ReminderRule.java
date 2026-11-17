package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "reminder_rules")
public class ReminderRule {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "channel")
    public String channel;
    @Column(name = "minutes_before")
    public Integer minutesBefore;
    @Column(name = "enabled")
    public Boolean enabled;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
