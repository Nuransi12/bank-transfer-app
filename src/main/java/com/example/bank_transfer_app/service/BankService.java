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
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private static final String FEE_ACCOUNT = "999999";
    private static final String EXTERNAL_ACCOUNT = "EXTERNAL";

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

    public BigDecimal getTotalSentByAccount(String accountNumber) {
        BigDecimal total = transactionRepository.getTotalSentByAccount(
                accountNumber, TransactionStatus.SUCCESS, EXTERNAL_ACCOUNT);
        return total != null ? total : BigDecimal.ZERO;
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
        validatePositiveAmount(amount);

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

        BigDecimal fee = amount.multiply(new BigDecimal("0.01"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal senderDebit = amount.add(fee);

        // Step 2: Debit the transfer amount and fee together.
        int rowsUpdated = customerRepository.debit(fromAccount, senderDebit);
        if (rowsUpdated == 0) {
            String reason = "Insufficient balance for transfer and fee in account " + fromAccount;
            auditService.recordFailure(fromAccount, toAccount, amount, reason);
            throw new InsufficientBalanceException(reason);
        }

        // Step 3: Experiment / Simulate failure
        if (simulateFailure) {
            auditService.recordFailure(fromAccount, toAccount, amount, "Simulated system failure after the withdrawal");
            throw new BankApiException("Simulated system failure after the withdrawal");
        }

        // Step 4: Credit the receiver and the fee account in the same transaction.
        if (customerRepository.credit(toAccount, amount) == 0) {
            throw new AccountNotFoundException("Receiver account not found: " + toAccount);
        }
        if (fee.signum() > 0 && customerRepository.credit(FEE_ACCOUNT, fee) == 0) {
            throw new AccountNotFoundException("Fee account not found: " + FEE_ACCOUNT);
        }

        // Step 5: Save SUCCESS record
        BankTransaction tx = BankTransaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .description("Transfer completed; 1% fee: LKR " + fee.toPlainString())
                .status(TransactionStatus.SUCCESS)
                .build();

        return transactionRepository.save(tx);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);

        if (customerRepository.credit(accountNumber, amount) == 0) {
            throw new AccountNotFoundException("Account " + accountNumber + " not found");
        }

        return transactionRepository.save(BankTransaction.builder()
                .fromAccount(EXTERNAL_ACCOUNT)
                .toAccount(accountNumber)
                .amount(amount)
                .description("Deposit")
                .status(TransactionStatus.SUCCESS)
                .build());
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);
        customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found"));

        if (customerRepository.debit(accountNumber, amount) == 0) {
            String reason = "Insufficient balance in account " + accountNumber;
            auditService.recordFailure(accountNumber, EXTERNAL_ACCOUNT, amount, reason);
            throw new InsufficientBalanceException(reason);
        }

        return transactionRepository.save(BankTransaction.builder()
                .fromAccount(accountNumber)
                .toAccount(EXTERNAL_ACCOUNT)
                .amount(amount)
                .description("Withdrawal")
                .status(TransactionStatus.SUCCESS)
                .build());
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        return transactionRepository.findLargeTransfers(
                min, TransactionStatus.SUCCESS, EXTERNAL_ACCOUNT);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankApiException("Amount must be greater than zero");
        }
    }
}