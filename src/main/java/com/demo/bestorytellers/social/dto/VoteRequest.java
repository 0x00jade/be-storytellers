package com.demo.bestorytellers.social.dto;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull Integer vote) {}
