package com.demo.bestorytellers.wallet.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

class WalletLockManagerTest {

    @Test
    void getLock_returnsSameLockForSameUser() {
        WalletLockManager manager = new WalletLockManager();
        UUID userId = UUID.randomUUID();

        ReentrantLock lock1 = manager.getLock(userId);
        ReentrantLock lock2 = manager.getLock(userId);

        assertThat(lock1).isSameAs(lock2);
    }

    @Test
    void getLock_returnsDifferentLocksForDifferentUsers() {
        WalletLockManager manager = new WalletLockManager();

        ReentrantLock lock1 = manager.getLock(UUID.randomUUID());
        ReentrantLock lock2 = manager.getLock(UUID.randomUUID());

        assertThat(lock1).isNotSameAs(lock2);
    }

    @Test
    void cleanup_removesUnlockedEntries() {
        WalletLockManager manager = new WalletLockManager();
        UUID idleUserId = UUID.randomUUID();
        UUID activeUserId = UUID.randomUUID();

        manager.getLock(idleUserId);
        ReentrantLock activeLock = manager.getLock(activeUserId);
        activeLock.lock();

        try {
            manager.cleanup();

            assertThat(manager.getLock(activeUserId)).isSameAs(activeLock);
            ReentrantLock newLock = manager.getLock(idleUserId);
            assertThat(newLock).isNotSameAs(activeLock);
        } finally {
            activeLock.unlock();
        }
    }

    @Test
    void cleanup_whenNoLocksHeld_removesAllEntries() {
        WalletLockManager manager = new WalletLockManager();
        manager.getLock(UUID.randomUUID());
        manager.getLock(UUID.randomUUID());

        manager.cleanup();

        UUID newUser = UUID.randomUUID();
        ReentrantLock fresh = manager.getLock(newUser);
        assertThat(fresh).isNotNull();
        assertThat(fresh.isLocked()).isFalse();
    }
}
