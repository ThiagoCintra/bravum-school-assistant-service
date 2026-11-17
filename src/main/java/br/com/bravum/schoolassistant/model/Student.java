package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "students")
public class Student {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "display_name")
    public String displayName;
    @Column(name = "status")
    public String status;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
