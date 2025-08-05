package com.application.orchestrator.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit

/**
 * Configuration for application caching.
 * Uses Caffeine cache for high-performance caching of frequently accessed data.
 * Includes automatic cache invalidation strategies.
 */
@Configuration
@EnableCaching
@EnableScheduling
open class CacheConfig {

    companion object {
        const val WORKFLOW_CACHE = "workflowCache"
        const val SAGA_STEP_CACHE = "sagaStepCache"
        const val ACTIVITY_RESULT_CACHE = "activityResultCache"
        
        // Cache TTL in seconds
        const val DEFAULT_CACHE_TTL = 300L // 5 minutes
        
        // Cache sizes
        const val WORKFLOW_CACHE_SIZE = 1000L
        const val SAGA_STEP_CACHE_SIZE = 2000L
        const val ACTIVITY_RESULT_CACHE_SIZE = 1000L
    }

    @Bean
    open fun cacheManager(): CacheManager {
        val cacheManager = SimpleCacheManager()
        
        val caches = listOf(
            ConcurrentMapCache(WORKFLOW_CACHE, 
                Caffeine.newBuilder()
                    .maximumSize(WORKFLOW_CACHE_SIZE)
                    .expireAfterWrite(DEFAULT_CACHE_TTL, TimeUnit.SECONDS)
                    .recordStats()
                    .build<Any, Any>()
                    .asMap(),
                false),
            ConcurrentMapCache(SAGA_STEP_CACHE,
                Caffeine.newBuilder()
                    .maximumSize(SAGA_STEP_CACHE_SIZE)
                    .expireAfterWrite(DEFAULT_CACHE_TTL, TimeUnit.SECONDS)
                    .recordStats()
                    .build<Any, Any>()
                    .asMap(),
                false),
            ConcurrentMapCache(ACTIVITY_RESULT_CACHE,
                Caffeine.newBuilder()
                    .maximumSize(ACTIVITY_RESULT_CACHE_SIZE)
                    .expireAfterWrite(DEFAULT_CACHE_TTL, TimeUnit.SECONDS)
                    .recordStats()
                    .build<Any, Any>()
                    .asMap(),
                false)
        )
        
        cacheManager.setCaches(caches)
        return cacheManager
    }
    
    /**
     * Scheduled task to clear all caches periodically.
     * This ensures that any stale data is eventually removed from the cache.
     * Runs every hour to prevent memory leaks and ensure data consistency.
     */
    @CacheEvict(value = [WORKFLOW_CACHE, SAGA_STEP_CACHE, ACTIVITY_RESULT_CACHE], allEntries = true)
    @Scheduled(fixedRateString = "\${cache.evict.interval:3600000}") // Default: 1 hour
    open fun evictAllCaches() {
        // Method intentionally left empty, the cache eviction is handled by the annotation
    }
}