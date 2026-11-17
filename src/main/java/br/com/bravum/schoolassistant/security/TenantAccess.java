package br.com.bravum.schoolassistant.security;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
public final class TenantAccess {
    private TenantAccess() {}
    public static UUID tenantId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UUID id)) throw new IllegalStateException("Tenant autenticado ausente");
        return id;
    }
}
