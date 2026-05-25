package com.demo.bestorytellers.notification.dto;

import com.demo.bestorytellers.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String payload, boolean isRead, Instant createdAt) {}
