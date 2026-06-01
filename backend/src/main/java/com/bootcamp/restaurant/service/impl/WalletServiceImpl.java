package com.bootcamp.restaurant.service.impl;

import com.bootcamp.restaurant.dto.WalletResp;
import com.bootcamp.restaurant.dto.WalletTransactionResp;
import com.bootcamp.restaurant.entity.WalletEntity;
import com.bootcamp.restaurant.entity.WalletTransactionEntity;
import com.bootcamp.restaurant.enums.TransactionType;
import com.bootcamp.restaurant.exception.ResourceNotFoundException;
import com.bootcamp.restaurant.mapper.WalletMapper;
import com.bootcamp.restaurant.model.WalletAdjustReq;
import com.bootcamp.restaurant.repository.WalletRepository;
import com.bootcamp.restaurant.repository.WalletTransactionRepository;
import com.bootcamp.restaurant.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private WalletMapper walletMapper;

    @Override
    public WalletResp getWalletByUserId(Long userId) {
        WalletEntity wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for userId: " + userId));
        return walletMapper.map(wallet);
    }

    @Override
    public List<WalletTransactionResp> getTransactions(Long walletId) {
        return transactionRepository.findByWalletEntity_WalletId(walletId)
                .stream()
                .map(walletMapper::map)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WalletTransactionResp recharge(Long walletId, WalletAdjustReq req) {
        return adjust(walletId, req, TransactionType.RECHARGE);
    }

    @Override
    @Transactional
    public WalletTransactionResp deduct(Long walletId, WalletAdjustReq req) {
        return adjust(walletId, req, TransactionType.DEDUCT);
    }

    @Override
    @Transactional
    public WalletTransactionResp refund(Long walletId, WalletAdjustReq req) {
        return adjust(walletId, req, TransactionType.REFUND);
    }

    private WalletTransactionResp adjust(Long walletId, WalletAdjustReq req, TransactionType type) {
        WalletEntity wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        BigDecimal newBalance = type == TransactionType.DEDUCT
                ? wallet.getBalance().subtract(req.amount())
                : wallet.getBalance().add(req.amount());

        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletTransactionEntity transaction = WalletTransactionEntity.builder()
                .walletEntity(wallet)
                .adminId(req.adminId())
                .amount(req.amount())
                .type(type)
                .description(req.description())
                .idempotencyKey(req.idempotencyKey())
                .createdAt(LocalDateTime.now())
                .build();

        return walletMapper.map(transactionRepository.save(transaction));
    }
}
