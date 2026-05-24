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

// Binds app.tenant_id inside every @Transactional method invoked by an
// authenticated request, so RLS filters tenant-scoped tables automatically.
// Order(1) + advisor pinned to order=0 in MultiTenancyConfig → we run INSIDE
// the transaction; set_config(.., true) is released at commit.
// No principal → no-op (the auth gate handles its own pre-tenant binding).
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
