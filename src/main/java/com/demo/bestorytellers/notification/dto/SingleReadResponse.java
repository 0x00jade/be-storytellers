package com.demo.bestorytellers.notification.dto;

import java.util.UUID;

public record SingleReadResponse(UUID id, boolean isRead) {}
