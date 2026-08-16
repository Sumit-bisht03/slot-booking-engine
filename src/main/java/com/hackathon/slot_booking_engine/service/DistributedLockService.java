package com.hackathon.slot_booking_engine.service;

import com.hackathon.slot_booking_engine.exception.ConcurrentBookingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(String lockKey, long waitTimeSeconds, long leaseTimeSeconds, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {

            acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("Failed to acquire lock with key {}", lockKey);
                throw new ConcurrentBookingException("Slot is currently being processed by another request. Please try again.");
            }
            log.info("Lock acquired successfully for key: {}", lockKey);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrentBookingException("Thread interrupted while waiting for lock");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released for key: {}", lockKey);
            }
        }
    }
}