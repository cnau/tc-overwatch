package com.tcoverwatch.common.multitenancy

import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

// Pin the @Transactional advisor to order=0 so TenantBindingAspect (@Order(1))
// runs INSIDE the transaction. Default LOWEST_PRECEDENCE would put the aspect
// outside the tx, breaking set_config(.., true)'s tx-LOCAL lifecycle.
@Configuration
@EnableTransactionManagement(order = 0)
class MultiTenancyConfig
