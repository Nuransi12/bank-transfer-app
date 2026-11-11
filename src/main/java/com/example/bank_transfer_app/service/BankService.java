package com.example.bank_transfer_app.service;
import com.example.bank_transfer_app.exception.AccountNotFoundException;
import com.example.bank_transfer_app.exception.BankApiException;
import com.example.bank_transfer_app.exception.InsufficientBalanceException;
import com.example.bank_transfer_app.model.BankTransaction;
import com.example.bank_transfer_app.model.Customer;
import com.example.bank_transfer_app.model.TransactionStatus;
import com.example.bank_transfer_app.repository.BankTransactionRepository;
import com.example.bank_transfer_app.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;
    private final TransactionAuditService auditService;

    public List<Customer> getAllCustomers(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return customerRepository.searchByName(keyword.trim());
        }
        return customerRepository.findAllOrdered();
    }

    public Customer getCustomerByAccount(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found"));
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    public BigDecimal getTotalBankBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public long getSuccessCount() {
        return transactionRepository.countByStatus(TransactionStatus.SUCCESS);
    }

    public long getFailedCount() {
        return transactionRepository.countByStatus(TransactionStatus.FAILED);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankApiException("Account number already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankApiException("Email already exists");
        }
        return customerRepository.save(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        // Step 1: Validate accounts
        if (fromAccount.equals(toAccount)) {
            auditService.recordFailure(fromAccount, toAccount, amount, "Accounts must be different");
            throw new BankApiException("Sender and receiver accounts must be different");
        }

        Customer sender = customerRepository.getByAccountNumber(fromAccount)
                .orElseThrow(() -> {
                    auditService.recordFailure(fromAccount, toAccount, amount, "Sender account not found");
                    return new AccountNotFoundException("Sender account not found: " + fromAccount);
                });

        Customer receiver = customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> {
                    auditService.recordFailure(fromAccount, toAccount, amount, "Receiver account not found");
                    return new AccountNotFoundException("Receiver account not found: " + toAccount);
                });

        // Step 2: Debit sender
        int rowsUpdated = customerRepository.debit(fromAccount, amount);
        if (rowsUpdated == 0) {
            auditService.recordFailure(fromAccount, toAccount, amount, "Insufficient balance in account " + fromAccount);
            throw new InsufficientBalanceException("Insufficient balance in account " + fromAccount);
        }

        // Step 3: Experiment / Simulate failure
        if (simulateFailure) {
            auditService.recordFailure(fromAccount, toAccount, amount, "Simulated system failure after the withdrawal");
            throw new BankApiException("Simulated system failure after the withdrawal");
        }

        // Step 4: Credit receiver
        customerRepository.credit(toAccount, amount);

        // Step 5: Save SUCCESS record
        BankTransaction tx = BankTransaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .description("Transfer completed")
                .status(TransactionStatus.SUCCESS)
                .build();

        return transactionRepository.save(tx);
    }
}