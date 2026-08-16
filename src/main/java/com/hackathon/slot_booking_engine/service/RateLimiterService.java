package com.hackathon.slot_booking_engine.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import jakarta.annotation.PostConstruct;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis-backed rate limiting. State lives in Redis (via Redisson's command
 * executor), not JVM heap - this is what makes the limit actually SHARED
 * across every instance of this app hitting the same Redis, and durable
 * across a restart of any one instance. Compare to a plain
 * ConcurrentHashMap<String, Bucket>, which only rate-limits within a single
 * JVM and forgets everything on restart.
 */
@Service
public class RateLimiterService {

    private final RedissonClient redissonClient;
    private ProxyManager<String> proxyManager;

    public RateLimiterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    void init() {
        // Bucket4j's Redisson extension needs the raw command executor,
        // which is only exposed on the concrete Redisson class - hence the cast.
        this.proxyManager = Bucket4jRedisson
                .casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
                .build();
    }

    public Bucket resolveBucket(String key) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.getProxy(key, configSupplier);
    }
}
