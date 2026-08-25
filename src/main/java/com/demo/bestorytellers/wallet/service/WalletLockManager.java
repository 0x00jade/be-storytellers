package com.demo.bestorytellers.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages per-user fair ReentrantLocks for wallet operations.
 *
 * Purpose: short-circuit concurrent same-user requests within this JVM before they
 * reach the DB, reducing lock contention on the PostgreSQL row lock.
 *
 * The DB-level PESSIMISTIC_WRITE lock in WalletRepository is the true safety net
 * across multiple JVM instances (clusters). This lock is a performance optimisation.
 */
@Component
public class WalletLockManager {

    private static final Logger log = LoggerFactory.getLogger(WalletLockManager.class);

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(UUID userId) {
        return locks.computeIfAbsent(userId, id -> new ReentrantLock(true));
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        int before = locks.size();
        locks.entrySet().removeIf(e -> !e.getValue().isLocked());
        int removed = before - locks.size();
        if (removed > 0) {
            log.debug("WalletLockManager cleanup: removed {} idle locks, {} remaining", removed, locks.size());
        }
    }
}
