package br.com.bravum.schoolassistant.alexa;

import br.com.bravum.schoolassistant.security.TenantAccess;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Vinculação sem Login Service; autorização sempre derivada da credencial Amazon vinculada. */
@Service
public class AlexaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("America/Sao_Paulo");
    private final JdbcTemplate db;
    private final byte[] pepper;
    private final byte[] aesKey;

    public AlexaService(JdbcTemplate db, @Value("${assistant.alexa.link-pepper:}") String secret,
            @Value("${assistant.alexa.encryption-key-base64:}") String aesBase64) {
        this.db = db;
        this.pepper = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(aesBase64 == null ? "" : aesBase64); }
        catch (IllegalArgumentException ex) { decoded = new byte[0]; }
        this.aesKey = decoded;
    }

    private void configured() {
        if (pepper.length < 32 || aesKey.length != 32)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Alexa não configurada");
    }
    public String hash(String input) {
        configured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String seal(String raw) {
        configured();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(aesKey,"AES"), new GCMParameterSpec(128,iv));
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String open(String sealed) {
        configured();
        try {
            byte[] raw = Base64.getDecoder().decode(sealed);
            if (raw.length < 29) throw new IllegalArgumentException("ciphertext inválido");
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[12], encrypted = new byte[raw.length-12];
            buffer.get(iv); buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aesKey,"AES"),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Falha na decifragem do destinatário", e); }
    }

    @Transactional
    public Map<String,Object> createGuardian(String fullName) {
        UUID tenant = TenantAccess.tenantId();
        if (fullName == null || fullName.isBlank() || fullName.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nome inválido");
        UUID guardian = UUID.randomUUID();
        db.update("INSERT INTO school_assistant.guardians(id,tenant_id,full_name) VALUES(?,?,?)",
                guardian,tenant,fullName.trim());
        return Map.of("guardianId",guardian);
    }

    @Transactional
    public Map<String,Object> createCode(UUID guardianId) {
        configured();
        UUID tenant = TenantAccess.tenantId();
        Integer allowed = db.queryForObject(
            "SELECT count(*) FROM school_assistant.guardians WHERE tenant_id=? AND id=? AND status='ACTIVE'",
            Integer.class,tenant,guardianId);
        if (allowed == null || allowed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Responsável não encontrado");
        db.update("DELETE FROM school_assistant.alexa_link_codes WHERE tenant_id=? AND guardian_id=?",
                tenant,guardianId);
        String code = Integer.toString(10_000_000 + RANDOM.nextInt(90_000_000));
        db.update("INSERT INTO school_assistant.alexa_link_codes(code_hash,tenant_id,guardian_id,expires_at) " +
            "VALUES(?,?,?,now()+interval '10 minutes')",hash("CODE:"+code),tenant,guardianId);
        return Map.of("code",code,"expiresInSeconds",600);
    }

    private record Link(UUID id, UUID tenantId, UUID guardianId, boolean subscribed) {}
    private Link linked(String alexaUserId) {
        List<Link> result = db.query("SELECT id,tenant_id,guardian_id,subscribed " +
                        "FROM school_assistant.alexa_account_links WHERE user_hash=? AND enabled=true",
                (rs,n)-> new Link((UUID)rs.getObject(1),(UUID)rs.getObject(2),
                        (UUID)rs.getObject(3),rs.getBoolean(4)),hash("USER:"+alexaUserId));
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Conta Alexa sem vínculo");
        return result.getFirst();
    }

    @Transactional
    public Map<String,String> link(String alexaUserId,String code) {
        configured();
        if (code == null || !code.matches("\\d{8}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Código inválido");
        String userHash = hash("USER:"+alexaUserId);
        var state = db.query("SELECT attempts,window_start,locked_until FROM school_assistant.alexa_link_attempts " +
                "WHERE user_hash=? FOR UPDATE",(rs,n) -> new Object[]{rs.getInt(1),rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3)==null?null:rs.getTimestamp(3).toInstant()},userHash);
        if (!state.isEmpty() && state.getFirst()[2] != null &&
                ((Instant)state.getFirst()[2]).isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Tentativas limitadas");
        List<UUID[]> matching = db.query("SELECT tenant_id,guardian_id FROM school_assistant.alexa_link_codes " +
                "WHERE code_hash=? AND consumed_at IS NULL AND expires_at>now() FOR UPDATE",
                (rs,n)->new UUID[]{(UUID)rs.getObject(1),(UUID)rs.getObject(2)},hash("CODE:"+code));
        if (matching.isEmpty()) {
            if (state.isEmpty() || ((Instant)state.getFirst()[1]).isBefore(Instant.now().minusSeconds(3600))) {
                db.update("INSERT INTO school_assistant.alexa_link_attempts(user_hash,attempts,window_start,locked_until) " +
                    "VALUES(?,1,now(),null) ON CONFLICT(user_hash) DO UPDATE SET attempts=1,window_start=now(),locked_until=null",
                        userHash);
            } else {
                int count = ((Number)state.getFirst()[0]).intValue()+1;
                db.update("UPDATE school_assistant.alexa_link_attempts SET attempts=?,locked_until=? WHERE user_hash=?",
                    count,count>=5?java.sql.Timestamp.from(Instant.now().plusSeconds(3600)):null,userHash);
            }
            return Map.of("status","INVALID_CODE");
        }
        UUID tenant = matching.getFirst()[0], guardian = matching.getFirst()[1];
        db.update("UPDATE school_assistant.alexa_link_codes SET consumed_at=now() WHERE code_hash=?",
                hash("CODE:"+code));
        db.update("INSERT INTO school_assistant.alexa_account_links(tenant_id,guardian_id,user_hash,encrypted_user_id) " +
            "VALUES(?,?,?,?) ON CONFLICT(user_hash) DO UPDATE SET tenant_id=excluded.tenant_id, " +
            "guardian_id=excluded.guardian_id, encrypted_user_id=excluded.encrypted_user_id,enabled=true, " +
            "subscribed=false,event_time=null,updated_at=now()",tenant,guardian,userHash,seal(alexaUserId));
        db.update("DELETE FROM school_assistant.alexa_link_attempts WHERE user_hash=?",userHash);
        return Map.of("status","LINKED");
    }

    @Transactional
    public Map<String,String> subscription(String alexaUserId,boolean enabled,Instant eventTime) {
        Link link = linked(alexaUserId);
        if (eventTime == null || eventTime.isAfter(Instant.now().plusSeconds(120)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Horário de evento inválido");
        db.update("UPDATE school_assistant.alexa_account_links SET subscribed=?,event_time=?,updated_at=now() " +
                    "WHERE id=? AND (event_time IS NULL OR event_time<=?)",
                enabled,java.sql.Timestamp.from(eventTime),link.id(),java.sql.Timestamp.from(eventTime));
        if (!enabled) {
            db.update("UPDATE school_assistant.alexa_notice_outbox SET status='CANCELLED' " +
                    "WHERE link_id=? AND status='PENDING'",link.id());
        }
        return Map.of("status","OK");
    }

    @Transactional(readOnly=true)
    public Map<String,Object> summary(String alexaUserId) {
        Link link = linked(alexaUserId);
        Long total = db.queryForObject("SELECT count(*) FROM school_assistant.communications " +
                    "WHERE tenant_id=? AND captured_at>=now()-interval '14 days'",
                Long.class,link.tenantId());
        Long pending = db.queryForObject("SELECT count(*) FROM school_assistant.assignments " +
                    "WHERE tenant_id=? AND status='PENDING' AND requires_review=false",Long.class,link.tenantId());
        return Map.of("recentCommunications",total,"pendingAssignments",pending);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> messages(String alexaUserId) {
        Link link = linked(alexaUserId);
        Long total = db.queryForObject("SELECT count(*) FROM school_assistant.communications " +
                    "WHERE tenant_id=? AND captured_at>=now()-interval '14 days'",Long.class,link.tenantId());
        List<Map<String,Object>> items = db.query(
                    "SELECT title FROM school_assistant.communications WHERE tenant_id=? " +
                    "AND captured_at>=now()-interval '14 days' ORDER BY captured_at DESC LIMIT 3",
                    (rs,n)->Map.of("title",rs.getString(1)==null?"Comunicado":rs.getString(1)),
                    link.tenantId());
        return Map.of("total",total,"items",items);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> agenda(String alexaUserId,String day) {
        Link link = linked(alexaUserId);
        if (!"hoje".equals(day) && !"amanhã".equals(day))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dia não permitido");
        LocalDate date = LocalDate.now(SCHOOL_ZONE).plusDays("amanhã".equals(day)?1:0);
        Instant start = date.atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        List<Map<String,Object>> items = db.query(
            "SELECT title FROM school_assistant.assignments WHERE tenant_id=? " +
            "AND due_at>=? AND due_at<? AND status='PENDING' AND requires_review=false " +
            "AND student_id IS NOT NULL ORDER BY due_at LIMIT 3",
            (rs,n)->Map.of("title",rs.getString(1)),link.tenantId(),
            java.sql.Timestamp.from(start),java.sql.Timestamp.from(end));
        return Map.of("items",items);
    }

    @Transactional
    public void onNewCommunication(UUID tenant,UUID communication) {
        db.update("INSERT INTO school_assistant.alexa_notice_outbox(tenant_id,link_id,communication_id,reference_id) " +
                "SELECT ?,l.id,?,concat('school-',md5(l.id::text||?::text)) " +
                "FROM school_assistant.alexa_account_links l WHERE l.tenant_id=? AND l.enabled=true AND l.subscribed=true " +
                "ON CONFLICT(link_id,communication_id) DO NOTHING",
                tenant,communication,communication,tenant);
    }

    @Transactional
    public void unlink(UUID tenant,UUID linkId) {
        db.update("UPDATE school_assistant.alexa_account_links SET enabled=false,subscribed=false,updated_at=now() " +
                  "WHERE id=? AND tenant_id=?",linkId,tenant);
        db.update("UPDATE school_assistant.alexa_notice_outbox SET status='CANCELLED' " +
                  "WHERE link_id=? AND tenant_id=? AND status='PENDING'",linkId,tenant);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> links(UUID tenant) {
        return db.query("SELECT id,guardian_id,subscribed,enabled FROM school_assistant.alexa_account_links " +
                "WHERE tenant_id=? ORDER BY created_at",
                (rs,n)->Map.of("id",rs.getObject(1),"guardianId",rs.getObject(2),
                        "subscribed",rs.getBoolean(3),"enabled",rs.getBoolean(4)),tenant);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> deliverySummary(UUID tenant) {
        List<Map<String,Object>> results = db.query("SELECT status,count(*) AS total FROM " +
                "school_assistant.alexa_notice_outbox WHERE tenant_id=? GROUP BY status",
                (rs,n)->Map.of("status",rs.getString(1),"total",rs.getLong(2)),tenant);
        return Map.of("byStatus",results);
    }
}
