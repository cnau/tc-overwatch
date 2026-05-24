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

// Mechanism + ordering pairing with MultiTenancyConfig in docs/claude/spring-boot.md § Multi-tenancy / RLS.
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
