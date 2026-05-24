package com.tcoverwatch.common.multitenancy

import com.tcoverwatch.common.security.AuthenticatedPrincipal
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

// Binds app.tenant_id on the Postgres connection for the duration of every
// @Transactional method invoked by an authenticated request, so RLS filters
// tenant-scoped tables automatically.
//
// Ordering: TransactionConfig pins @EnableTransactionManagement(order = 0) and
// this aspect is @Order(1), so we run INSIDE the transaction Spring opened —
// the set_config(.., true) lives for that one tx and is released at commit.
//
// No principal in SecurityContext → no-op. That's the path the auth gate
// (AuthService.signIn) takes — it sets app.tenant_id itself once it knows
// the tenant, and uses SECURITY DEFINER for the pre-tenant lookup.
@Aspect
@Component
@Order(1)
class TenantBindingAspect(
    @PersistenceContext private val entityManager: EntityManager,
) {
    @Around(
        "@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)",
    )
    fun bindTenant(joinPoint: ProceedingJoinPoint): Any? {
        val tenantId =
            SecurityContextHolder
                .getContext()
                .authentication
                ?.principal
                ?.let { it as? AuthenticatedPrincipal }
                ?.tenantId
        if (tenantId != null) {
            entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .singleResult
        }
        return joinPoint.proceed()
    }
}
