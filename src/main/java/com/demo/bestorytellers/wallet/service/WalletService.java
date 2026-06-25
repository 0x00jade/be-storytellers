package com.demo.bestorytellers.wallet.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ResourceNotFoundException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import com.demo.bestorytellers.wallet.dto.DepositRequest;
import com.demo.bestorytellers.wallet.dto.PurchaseResponse;
import com.demo.bestorytellers.wallet.dto.TransactionResponse;
import com.demo.bestorytellers.wallet.dto.WalletResponse;
import com.demo.bestorytellers.wallet.entity.ChapterPurchase;
import com.demo.bestorytellers.wallet.entity.TransactionStatus;
import com.demo.bestorytellers.wallet.entity.TransactionType;
import com.demo.bestorytellers.wallet.entity.Wallet;
import com.demo.bestorytellers.wallet.entity.WalletTransaction;
import com.demo.bestorytellers.wallet.repository.ChapterPurchaseRepository;
import com.demo.bestorytellers.wallet.repository.WalletRepository;
import com.demo.bestorytellers.wallet.repository.WalletTransactionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final ChapterPurchaseRepository purchaseRepository;
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final PaymentGatewayService paymentGateway;
    private final WalletLockManager lockManager;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository transactionRepository,
                         ChapterPurchaseRepository purchaseRepository,
                         StoryRepository storyRepository,
                         ChapterRepository chapterRepository,
                         UserRepository userRepository,
                         PaymentGatewayService paymentGateway,
                         WalletLockManager lockManager) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.purchaseRepository = purchaseRepository;
        this.storyRepository = storyRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.lockManager = lockManager;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = getOrCreateWallet(userId);
        List<TransactionResponse> recent = transactionRepository
            .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(0, 20))
            .stream()
            .map(this::toTransactionResponse)
            .collect(Collectors.toList());
        return new WalletResponse(wallet.getBalance(), "USD", recent);
    }

    /**
     * Deposits money into the user's wallet.
     *
     * Technique 1 — Optimistic locking (@Version on Wallet) + @Retryable:
     * Low contention on deposits means we prefer optimism over blocking.
     * If two deposits race, one gets OptimisticLockingFailureException and retries
     * (up to 3 times with exponential backoff). Each retry gets a fresh transaction.
     *
     * Idempotency: if the same idempotencyKey is replayed (network retry from client),
     * we return the original completed transaction without charging again.
     */
    @Retryable(
        retryFor = OptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public TransactionResponse deposit(UUID userId, DepositRequest request) {
        // Idempotency check: if same key was already processed, replay it safely
        return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
            .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
            .map(this::toTransactionResponse)
            .orElseGet(() -> executeDeposit(userId, request));
    }

    private TransactionResponse executeDeposit(UUID userId, DepositRequest request) {
        Wallet wallet = getOrCreateWallet(userId);

        if (wallet.isFrozen()) {
            throw new ValidationException("Wallet is frozen");
        }

        // Create PENDING transaction record before charging — audit trail even on failure
        WalletTransaction tx = new WalletTransaction(
            wallet, TransactionType.DEPOSIT, TransactionStatus.PENDING,
            request.amount(), request.idempotencyKey(), null,
            "Deposit via payment token " + request.paymentMethodToken()
        );
        transactionRepository.save(tx);

        // Call payment gateway — may throw if payment is declined
        paymentGateway.charge(request.paymentMethodToken(), request.amount());

        // Credit wallet — @Version causes OptimisticLockingFailureException if stale
        wallet.setBalance(wallet.getBalance().add(request.amount()));
        walletRepository.save(wallet);

        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);

        return toTransactionResponse(tx);
    }

    /**
     * Purchases access to a premium chapter.
     *
     * Technique 2 — ReentrantLock (JVM-level per-user guard):
     * Prevents two concurrent requests from the same user reaching the DB lock.
     * tryLock(3s) fails fast rather than queuing indefinitely.
     *
     * Technique 3 — Pessimistic DB lock (PESSIMISTIC_WRITE = SELECT ... FOR UPDATE):
     * The true safety net across multiple JVM instances. Blocks at the PostgreSQL level
     * until the current transaction commits, preventing concurrent overdraft.
     */
    @Transactional
    public PurchaseResponse purchaseChapter(UUID userId, String slug, int number) {
        // Acquire JVM lock first to short-circuit within this instance
        ReentrantLock lock = lockManager.getLock(userId);
        try {
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                throw new ValidationException("Another purchase is in progress, please try again");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Purchase interrupted, please try again");
        }

        try {
            return executePurchase(userId, slug, number);
        } finally {
            lock.unlock();
        }
    }

    private PurchaseResponse executePurchase(UUID userId, String slug, int number) {
        Story story = storyRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + slug));
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(story.getId(), number)
            .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + number));

        if (chapter.getStatus() != ChapterStatus.PUBLISHED) {
            throw new ValidationException("Chapter is not published");
        }
        if (chapter.getPrice() == null) {
            throw new ValidationException("This chapter is free — no purchase needed");
        }

        // Idempotent: already purchased
        if (purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())) {
            throw new ConflictException("Chapter already purchased");
        }

        // Authors read their own chapters for free
        if (story.getAuthor().getId().equals(userId)) {
            throw new ValidationException("Authors do not pay to read their own chapters");
        }

        // PESSIMISTIC_WRITE: issues SELECT ... FOR UPDATE — blocks any concurrent deduction
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(new Wallet(user)));

        if (wallet.isFrozen()) {
            throw new ValidationException("Wallet is frozen");
        }

        BigDecimal price = chapter.getPrice();
        if (wallet.getBalance().compareTo(price) < 0) {
            throw new ValidationException("Insufficient wallet balance. Required: " + price
                + ", available: " + wallet.getBalance());
        }

        // Deduct balance
        wallet.setBalance(wallet.getBalance().subtract(price));
        walletRepository.save(wallet);

        // Record the transaction
        WalletTransaction tx = new WalletTransaction(
            wallet, TransactionType.PURCHASE, TransactionStatus.COMPLETED,
            price, null, chapter.getId(),
            "Chapter " + number + " of \"" + story.getTitle() + "\""
        );
        transactionRepository.save(tx);

        // Grant permanent access
        purchaseRepository.save(new ChapterPurchase(user, chapter, tx.getId()));

        return new PurchaseResponse(
            chapter.getId(), chapter.getChapterNumber(), price, wallet.getBalance(), Instant.now());
    }

    private Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return walletRepository.save(new Wallet(user));
        });
    }

    private TransactionResponse toTransactionResponse(WalletTransaction tx) {
        return new TransactionResponse(
            tx.getId(), tx.getType().name(), tx.getStatus().name(),
            tx.getAmount(), tx.getDescription(), tx.getCreatedAt());
    }
}
