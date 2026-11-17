package br.com.bravum.schoolassistant.alexa;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Worker idempotente; envio real explicitamente desligado por padrão. */
@Component
public class AlexaPublisher {
    private final JdbcTemplate db;
    private final AlexaService linkService;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final boolean enabled;
    private final String clientId,clientSecret,region,stage;

    public AlexaPublisher(JdbcTemplate db, AlexaService linkService, ObjectMapper json,
            PlatformTransactionManager transactionManager,
            @Value("${assistant.alexa.notifications-enabled:false}") boolean enabled,
            @Value("${assistant.alexa.lwa-client-id:}") String clientId,
            @Value("${assistant.alexa.lwa-client-secret:}") String clientSecret,
            @Value("${assistant.alexa.event-region:NA}") String region,
            @Value("${assistant.alexa.stage:development}") String stage) {
        this.db=db;this.linkService=linkService;this.json=json;
        this.tx=new TransactionTemplate(transactionManager);
        this.enabled=enabled;this.clientId=clientId;this.clientSecret=clientSecret;this.region=region;this.stage=stage;
    }
    private record Delivery(UUID id,String reference,String encryptedUser) {}

    @Scheduled(fixedDelayString="${ALEXA_PUBLISH_INTERVAL_MS:60000}")
    public void dispatch() {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank()
                || !List.of("NA","EU","FE").contains(region)
                || !List.of("development","production").contains(stage)) return;
        for(int i=0;i<10;i++) {
            boolean processed=Boolean.TRUE.equals(tx.execute(s -> {
                List<Delivery> due=db.query(
                    "SELECT o.id,o.reference_id,l.encrypted_user_id FROM school_assistant.alexa_notice_outbox o " +
                    "JOIN school_assistant.alexa_account_links l ON l.id=o.link_id AND l.tenant_id=o.tenant_id " +
                    "WHERE o.status='PENDING' AND o.next_attempt_at<=now() AND l.subscribed=true AND l.enabled=true " +
                    "ORDER BY o.created_at LIMIT 1 FOR UPDATE OF o SKIP LOCKED",
                    (rs,n)->new Delivery((UUID)rs.getObject(1),rs.getString(2),rs.getString(3)));
                if(due.isEmpty()) return false;
                Delivery d=due.getFirst();
                try {
                    send(linkService.open(d.encryptedUser()),d.reference());
                    db.update("UPDATE school_assistant.alexa_notice_outbox SET status='SENT',sent_at=now(),attempts=attempts+1,last_error=null WHERE id=?",d.id());
                } catch(Exception ex) {
                    db.update("UPDATE school_assistant.alexa_notice_outbox SET attempts=attempts+1, " +
                            "status=CASE WHEN attempts>=4 THEN 'FAILED' ELSE 'PENDING' END, " +
                            "next_attempt_at=now()+((power(2,least(attempts,5))::int)*interval '1 minute'), " +
                            "last_error=? WHERE id=?","PROVIDER_ERROR",d.id());
                }
                return true;
            }));
            if(!processed) break;
        }
    }

    void send(String userId,String reference) throws Exception {
        HttpRequest tokenRequest=HttpRequest.newBuilder(URI.create("https://api.amazon.com/auth/o2/token"))
            .timeout(Duration.ofSeconds(8)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                "grant_type=client_credentials&client_id="+enc(clientId)+"&client_secret="+enc(clientSecret)+
                "&scope="+enc("alexa::proactive_events"))).build();
        HttpResponse<String> tokenResponse=http.send(tokenRequest,HttpResponse.BodyHandlers.ofString());
        if(tokenResponse.statusCode()!=200) throw new IllegalStateException("LWA indisponível");
        String token=json.readTree(tokenResponse.body()).path("access_token").asText();
        if(token.isBlank())throw new IllegalStateException("LWA vazio");
        String host=switch(region) {
            case "EU"->"api.eu.amazonalexa.com";
            case "FE"->"api.fe.amazonalexa.com";
            default->"api.amazonalexa.com";
        };
        String endpoint="https://"+host+"/v1/proactiveEvents"+
                ("development".equals(stage)?"/stages/development":"");
        Instant now=Instant.now();
        Map<String,Object> event=Map.of(
            "timestamp",now.toString(),
            "referenceId",reference,
            "expiryTime",now.plus(Duration.ofHours(2)).toString(),
            "event",Map.of(
                "name","AMAZON.MessageAlert.Activated",
                "payload",Map.of(
                    "state",Map.of("status","UNREAD","freshness","NEW"),
                    "messageGroup",Map.of("creator",Map.of("name","Assistente Escolar"),"count",1))),
            "relevantAudience",Map.of("type","Unicast","payload",Map.of("user",userId)));
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type","application/json").header("Authorization","Bearer "+token)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(event))).build();
        HttpResponse<String> res=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=202) throw new IllegalStateException("Alexa recusou evento HTTP "+res.statusCode());
    }
    private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
}
