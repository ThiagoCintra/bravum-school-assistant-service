package br.com.bravum.schoolassistant.alexa;

import br.com.bravum.schoolassistant.security.TenantAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AlexaController {
    private final AlexaService service;
    public AlexaController(AlexaService service){this.service=service;}

    public record GuardianInput(@NotBlank String fullName) {}
    public record LinkInput(@NotNull String code) {}
    public record SubscriptionInput(boolean enabled,@NotNull Instant eventTime) {}

    private String user() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping("/alexa/admin/guardians")
    public Map<String,Object> guardian(@Valid @RequestBody GuardianInput input) {
        return service.createGuardian(input.fullName());
    }
    @PostMapping("/alexa/admin/link-codes/{guardianId}")
    public Map<String,Object> issue(@PathVariable UUID guardianId) { return service.createCode(guardianId); }
    @GetMapping("/alexa/admin/links")
    public List<Map<String,Object>> links() {return service.links(TenantAccess.tenantId());}
    @DeleteMapping("/alexa/admin/links/{linkId}")
    public void unlink(@PathVariable UUID linkId){service.unlink(TenantAccess.tenantId(),linkId);}
    @GetMapping("/alexa/admin/notifications")
    public Map<String,Object> notifications(){return service.deliverySummary(TenantAccess.tenantId());}

    @PostMapping("/alexa/skill/link")
    public Map<String,String> link(@Valid @RequestBody LinkInput input){return service.link(user(),input.code());}
    @PostMapping("/alexa/skill/subscription")
    public Map<String,String> subscription(@Valid @RequestBody SubscriptionInput input) {
        return service.subscription(user(),input.enabled(),input.eventTime());
    }
    @GetMapping("/alexa/skill/summary")
    public Map<String,Object> summary(){return service.summary(user());}
    @GetMapping("/alexa/skill/messages")
    public Map<String,Object> messages(){return service.messages(user());}
    @GetMapping("/alexa/skill/agenda")
    public Map<String,Object> agenda(@RequestParam(defaultValue="amanhã") String day) {
        return service.agenda(user(),day);
    }
}
