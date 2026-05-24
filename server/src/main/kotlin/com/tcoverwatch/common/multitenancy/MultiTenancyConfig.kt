package com.tcoverwatch.common.multitenancy

import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

// Pin the @Transactional advisor's order to 0 so TenantBindingAspect (@Order(1))
// can fire INSIDE the transaction Spring opens. Default order is
// Ordered.LOWEST_PRECEDENCE, which would put any other aspect outside the tx
// and break the set_config(.., true) lifecycle.
@Configuration
@EnableTransactionManagement(order = 0)
class MultiTenancyConfig
