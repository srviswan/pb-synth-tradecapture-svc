package com.pb.synth.tradecapture.cache;

import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify Hazelcast configuration and beans are loaded correctly.
 */
@SpringBootTest
@ActiveProfiles("test-mocked")
@TestPropertySource(properties = {
    "cache.provider=hazelcast",
    "hazelcast.mode=embedded",
    "hazelcast.cluster-name=test-cluster"
})
@DisplayName("Hazelcast Configuration Test")
class HazelcastConfigurationTest {

    @Autowired(required = false)
    private HazelcastInstance hazelcastInstance;

    @Autowired(required = false)
    private DistributedLockService distributedLockService;

    @Autowired(required = false)
    private DistributedCacheService distributedCacheService;

    @Test
    @DisplayName("should load Hazelcast instance when cache provider is hazelcast")
    void should_LoadHazelcastInstance_When_ProviderIsHazelcast() {
        assertThat(hazelcastInstance).isNotNull();
        assertThat(hazelcastInstance.getName()).isNotNull();
    }

    @Test
    @DisplayName("should load DistributedLockService implementation")
    void should_LoadDistributedLockService_When_HazelcastEnabled() {
        assertThat(distributedLockService).isNotNull();
        assertThat(distributedLockService.getClass().getName())
            .contains("Hazelcast");
    }

    @Test
    @DisplayName("should load DistributedCacheService implementation")
    void should_LoadDistributedCacheService_When_HazelcastEnabled() {
        assertThat(distributedCacheService).isNotNull();
        assertThat(distributedCacheService.getClass().getName())
            .contains("Hazelcast");
    }

    @Test
    @DisplayName("should be able to acquire and release lock")
    void should_AcquireAndReleaseLock_When_UsingHazelcast() {
        String partitionKey = "TEST_PARTITION";
        
        boolean acquired = distributedLockService.acquireLock(partitionKey);
        assertThat(acquired).isTrue();
        
        boolean isLocked = distributedLockService.isLocked(partitionKey);
        assertThat(isLocked).isTrue();
        
        distributedLockService.releaseLock(partitionKey);
        
        boolean isLockedAfterRelease = distributedLockService.isLocked(partitionKey);
        assertThat(isLockedAfterRelease).isFalse();
    }

    @Test
    @DisplayName("should be able to set and get cache values")
    void should_SetAndGetCacheValues_When_UsingHazelcast() {
        String key = "test:key";
        String value = "test-value";
        
        distributedCacheService.set(key, value, java.time.Duration.ofMinutes(5));
        
        var result = distributedCacheService.get(key);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(value);
        
        boolean exists = distributedCacheService.exists(key);
        assertThat(exists).isTrue();
        
        boolean deleted = distributedCacheService.delete(key);
        assertThat(deleted).isTrue();
        
        var resultAfterDelete = distributedCacheService.get(key);
        assertThat(resultAfterDelete).isEmpty();
    }
}

