package com.demo.bestorytellers.wallet.controller;

import com.demo.bestorytellers.auth.security.UserPrincipal;
import com.demo.bestorytellers.common.dto.ApiResponse;
import com.demo.bestorytellers.wallet.dto.DepositRequest;
import com.demo.bestorytellers.wallet.dto.TransactionResponse;
import com.demo.bestorytellers.wallet.dto.WalletResponse;
import com.demo.bestorytellers.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWallet(principal.getUserId())));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody DepositRequest request
    ) {
        return ResponseEntity.ok(
            ApiResponse.ok(walletService.deposit(principal.getUserId(), request)));
    }
}
