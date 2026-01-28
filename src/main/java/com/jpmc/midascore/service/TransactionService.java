package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IncentiveService incentiveService;

    public TransactionService(UserRepository userRepository,
            TransactionRepository transactionRepository,
            IncentiveService incentiveService) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.incentiveService = incentiveService;
    }

    @Transactional
    public boolean processTransaction(Transaction transaction) {
        // Find sender and recipient
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        // Validate: sender must exist
        if (sender == null) {
            logger.debug("Transaction rejected: sender with id {} not found", transaction.getSenderId());
            return false;
        }

        // Validate: recipient must exist
        if (recipient == null) {
            logger.debug("Transaction rejected: recipient with id {} not found", transaction.getRecipientId());
            return false;
        }

        // Validate: sender must have sufficient balance
        if (sender.getBalance() < transaction.getAmount()) {
            logger.debug("Transaction rejected: sender {} has insufficient balance ({} < {})",
                    sender.getName(), sender.getBalance(), transaction.getAmount());
            return false;
        }

        // Get incentive from the incentive API
        Incentive incentive = incentiveService.getIncentive(transaction);
        float incentiveAmount = incentive != null ? incentive.getAmount() : 0;

        // Process the transaction: update balances
        // Sender loses the transaction amount
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        // Recipient gains the transaction amount PLUS the incentive
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        // Save updated users
        userRepository.save(sender);
        userRepository.save(recipient);

        // Record the transaction with incentive
        TransactionRecord transactionRecord = new TransactionRecord(sender, recipient, transaction.getAmount(),
                incentiveAmount);
        transactionRepository.save(transactionRecord);

        logger.info("Transaction processed: {} -> {}, amount: {}, incentive: {}",
                sender.getName(), recipient.getName(), transaction.getAmount(), incentiveAmount);

        return true;
    }
}
