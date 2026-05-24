package com.tcoverwatch.common.multitenancy

import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

// Advisor order=0 pairs with TenantBindingAspect @Order(1) so the aspect runs INSIDE the tx.
@Configuration
@EnableTransactionManagement(order = 0)
class MultiTenancyConfig
