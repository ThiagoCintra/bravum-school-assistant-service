package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "provider_student_contexts")
public class StudentContext {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "connection_id")
    public UUID connectionId;
    @Column(name = "context_key")
    public String contextKey;
    @Column(name = "external_student_id")
    public String externalStudentId;
    @Column(name = "student_id")
    public UUID studentId;
    @Column(name = "mapping_status")
    public String mappingStatus;
    @Column(name = "last_seen_at")
    public Instant lastSeenAt;
    @Column(name = "created_at")
    public Instant createdAt;
}
