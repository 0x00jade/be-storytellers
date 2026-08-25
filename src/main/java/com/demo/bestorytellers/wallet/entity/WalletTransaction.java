package com.demo.bestorytellers.wallet.entity;

import com.demo.bestorytellers.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    protected WalletTransaction() {}

    public WalletTransaction(Wallet wallet, TransactionType type, TransactionStatus status,
                              BigDecimal amount, String idempotencyKey, UUID referenceId,
                              String description) {
        this.wallet = wallet;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.referenceId = referenceId;
        this.description = description;
    }

    public UUID getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getReferenceId() { return referenceId; }
    public String getDescription() { return description; }

    public void setStatus(TransactionStatus status) { this.status = status; }
}
