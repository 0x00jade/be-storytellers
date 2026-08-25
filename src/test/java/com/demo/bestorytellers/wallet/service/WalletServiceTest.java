package com.demo.bestorytellers.wallet.service;

import com.demo.bestorytellers.chapter.entity.Chapter;
import com.demo.bestorytellers.chapter.entity.ChapterStatus;
import com.demo.bestorytellers.chapter.repository.ChapterRepository;
import com.demo.bestorytellers.common.exception.ConflictException;
import com.demo.bestorytellers.common.exception.ValidationException;
import com.demo.bestorytellers.story.entity.MaturityRating;
import com.demo.bestorytellers.story.entity.Story;
import com.demo.bestorytellers.story.repository.StoryRepository;
import com.demo.bestorytellers.user.entity.User;
import com.demo.bestorytellers.user.repository.UserRepository;
import com.demo.bestorytellers.wallet.dto.DepositRequest;
import com.demo.bestorytellers.wallet.dto.PurchaseResponse;
import com.demo.bestorytellers.wallet.dto.TransactionResponse;
import com.demo.bestorytellers.wallet.entity.TransactionStatus;
import com.demo.bestorytellers.wallet.entity.TransactionType;
import com.demo.bestorytellers.wallet.entity.Wallet;
import com.demo.bestorytellers.wallet.entity.WalletTransaction;
import com.demo.bestorytellers.wallet.repository.ChapterPurchaseRepository;
import com.demo.bestorytellers.wallet.repository.WalletRepository;
import com.demo.bestorytellers.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private ChapterPurchaseRepository purchaseRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGatewayService paymentGateway;
    @Mock private WalletLockManager lockManager;

    private WalletService service;

    private UUID userId;
    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() throws InterruptedException {
        service = new WalletService(walletRepository, transactionRepository, purchaseRepository,
            storyRepository, chapterRepository, userRepository, paymentGateway, lockManager);

        userId = UUID.randomUUID();
        user = new User("test@email.com", "testuser", "Test User", null, "GOOGLE", "google-123");
        wallet = new Wallet(user);

        // Default: lock manager returns a real ReentrantLock
        java.util.concurrent.locks.ReentrantLock realLock = new java.util.concurrent.locks.ReentrantLock();
        when(lockManager.getLock(any(UUID.class))).thenReturn(realLock);
    }

    // ─── deposit() ───────────────────────────────────────────────────────────

    @Test
    void deposit_happyPath_creditsWalletAndReturnsCompletedTransaction() {
        DepositRequest req = new DepositRequest(BigDecimal.TEN, "idem-key-1", "tok_visa");
        when(transactionRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(paymentGateway.charge(any(), any())).thenReturn("charge_123");
        WalletTransaction saved = new WalletTransaction(wallet, TransactionType.DEPOSIT,
            TransactionStatus.COMPLETED, BigDecimal.TEN, "idem-key-1", null, "desc");
        when(transactionRepository.save(any())).thenReturn(saved);
        when(walletRepository.save(any())).thenReturn(wallet);

        TransactionResponse response = service.deposit(userId, req);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.TEN);
        verify(walletRepository).save(wallet);
    }

    @Test
    void deposit_idempotentReplay_returnsOriginalTransactionWithoutCharging() {
        DepositRequest req = new DepositRequest(BigDecimal.TEN, "idem-key-dup", "tok_visa");
        WalletTransaction existing = new WalletTransaction(wallet, TransactionType.DEPOSIT,
            TransactionStatus.COMPLETED, BigDecimal.TEN, "idem-key-dup", null, "desc");
        when(transactionRepository.findByIdempotencyKey("idem-key-dup")).thenReturn(Optional.of(existing));

        TransactionResponse response = service.deposit(userId, req);

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(paymentGateway, never()).charge(any(), any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void deposit_frozenWallet_throwsValidationException() {
        wallet.setFrozen(true);
        DepositRequest req = new DepositRequest(BigDecimal.TEN, "idem-key-2", "tok_visa");
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.deposit(userId, req))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("frozen");
    }

    // ─── purchaseChapter() ────────────────────────────────────────────────────

    @Test
    void purchaseChapter_happyPath_deductsBalanceAndGrantsAccess() {
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(storyId, UUID.randomUUID());  // different author
        Chapter chapter = mockChapter(story, new BigDecimal("1.99"));
        wallet.setBalance(new BigDecimal("10.00"));

        when(storyRepository.findBySlug("my-story")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1)).thenReturn(Optional.of(chapter));
        when(purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);
        WalletTransaction tx = new WalletTransaction(wallet, TransactionType.PURCHASE,
            TransactionStatus.COMPLETED, new BigDecimal("1.99"), null, chapter.getId(), "desc");
        when(transactionRepository.save(any())).thenReturn(tx);

        PurchaseResponse response = service.purchaseChapter(userId, "my-story", 1);

        assertThat(response.amountCharged()).isEqualByComparingTo("1.99");
        assertThat(response.newBalance()).isEqualByComparingTo("8.01");
        verify(purchaseRepository).save(any());
    }

    @Test
    void purchaseChapter_insufficientBalance_throwsValidationException() {
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(storyId, UUID.randomUUID());
        Chapter chapter = mockChapter(story, new BigDecimal("5.00"));
        wallet.setBalance(new BigDecimal("1.00"));

        when(storyRepository.findBySlug("my-story")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1)).thenReturn(Optional.of(chapter));
        when(purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.purchaseChapter(userId, "my-story", 1))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Insufficient wallet balance");
    }

    @Test
    void purchaseChapter_alreadyPurchased_throwsConflictException() {
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(storyId, UUID.randomUUID());
        Chapter chapter = mockChapter(story, new BigDecimal("1.99"));

        when(storyRepository.findBySlug("my-story")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1)).thenReturn(Optional.of(chapter));
        when(purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.purchaseChapter(userId, "my-story", 1))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already purchased");
    }

    @Test
    void purchaseChapter_freeChapter_throwsValidationException() {
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(storyId, UUID.randomUUID());
        Chapter chapter = mockChapter(story, null);  // null price = free

        when(storyRepository.findBySlug("my-story")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1)).thenReturn(Optional.of(chapter));
        when(purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.purchaseChapter(userId, "my-story", 1))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("free");
    }

    @Test
    void purchaseChapter_draftChapter_throwsValidationException() {
        UUID storyId = UUID.randomUUID();
        Story story = mockStory(storyId, UUID.randomUUID());
        Chapter chapter = mockChapter(story, new BigDecimal("1.99"));
        chapter.setStatus(ChapterStatus.DRAFT);

        when(storyRepository.findBySlug("my-story")).thenReturn(Optional.of(story));
        when(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1)).thenReturn(Optional.of(chapter));
        when(purchaseRepository.existsByIdUserIdAndIdChapterId(userId, chapter.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.purchaseChapter(userId, "my-story", 1))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("not published");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Story mockStory(UUID storyId, UUID authorId) {
        User author = new User("author@email.com", "author", "Author", null, "GOOGLE", "g-456");
        org.springframework.test.util.ReflectionTestUtils.setField(author, "id", authorId);
        Story story = new Story(author, "My Story",
            "my-story-" + storyId.toString().substring(0, 8),
            "desc", "en", MaturityRating.EVERYONE);
        org.springframework.test.util.ReflectionTestUtils.setField(story, "id", storyId);
        return story;
    }

    private Chapter mockChapter(Story story, BigDecimal price) {
        Chapter chapter = new Chapter(story, "Chapter One", 1);
        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setWordCount(200);
        chapter.setPrice(price);
        org.springframework.test.util.ReflectionTestUtils.setField(chapter, "id", UUID.randomUUID());
        return chapter;
    }
}
