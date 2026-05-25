package com.demo.bestorytellers.notification.repository;

import com.demo.bestorytellers.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(value = "SELECT n FROM Notification n WHERE n.user.id = :userId ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId")
    Page<Notification> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n WHERE n.user.id = :userId AND n.read = false ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.read = false")
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
        DELETE FROM notifications WHERE user_id = :userId AND is_read = false
        AND id NOT IN (
            SELECT id FROM notifications WHERE user_id = :userId AND is_read = false
            ORDER BY created_at DESC LIMIT 200
        )
        """, nativeQuery = true)
    void purgeExcessUnread(@Param("userId") UUID userId);
}
