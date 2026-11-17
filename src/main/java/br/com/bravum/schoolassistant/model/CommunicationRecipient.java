package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "communication_recipients")
public class CommunicationRecipient {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "communication_id")
    public UUID communicationId;
    @Column(name = "student_id")
    public UUID studentId;
    @Column(name = "student_context_id")
    public UUID studentContextId;
    @Column(name = "mapping_status")
    public String mappingStatus;
    @Column(name = "created_at")
    public Instant createdAt;
}
