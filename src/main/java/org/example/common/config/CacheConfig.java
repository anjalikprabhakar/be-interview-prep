package org.example.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Turns on @Cacheable/@CachePut/@CacheEvict. The Caffeine cache itself is configured in application.yml. */
@Configuration
@EnableCaching
public class CacheConfig {
}
