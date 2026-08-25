package com.demo.bestorytellers.wallet.entity;

import com.demo.bestorytellers.common.entity.BaseEntity;
import com.demo.bestorytellers.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Optimistic lock counter. Incremented by JPA on every UPDATE.
     * Causes OptimisticLockingFailureException if two concurrent deposits race.
     * @Retryable in WalletService.deposit() catches this and retries.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "is_frozen", nullable = false)
    private boolean frozen = false;

    protected Wallet() {}

    public Wallet(User user) {
        this.user = user;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getBalance() { return balance; }
    public Long getVersion() { return version; }
    public boolean isFrozen() { return frozen; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
}
