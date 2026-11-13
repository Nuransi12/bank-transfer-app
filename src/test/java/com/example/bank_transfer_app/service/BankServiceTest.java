package com.example.bank_transfer_app.service;

import com.example.bank_transfer_app.exception.InsufficientBalanceException;
import com.example.bank_transfer_app.model.BankTransaction;
import com.example.bank_transfer_app.model.Customer;
import com.example.bank_transfer_app.model.TransactionStatus;
import com.example.bank_transfer_app.repository.BankTransactionRepository;
import com.example.bank_transfer_app.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankServiceTest {

    private CustomerRepository customerRepository;
    private BankTransactionRepository transactionRepository;
    private TransactionAuditService auditService;
    private BankService bankService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        transactionRepository = mock(BankTransactionRepository.class);
        auditService = mock(TransactionAuditService.class);
        bankService = new BankService(customerRepository, transactionRepository, auditService);
        when(transactionRepository.save(any(BankTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void transferDebitsFeeAndCreditsReceiverAndFeeAccount() {
        when(customerRepository.getByAccountNumber("100001"))
                .thenReturn(Optional.of(customer("100001")));
        when(customerRepository.getByAccountNumber("100002"))
                .thenReturn(Optional.of(customer("100002")));
        when(customerRepository.debit(eq("100001"), any(BigDecimal.class))).thenReturn(1);
        when(customerRepository.credit(eq("100002"), any(BigDecimal.class))).thenReturn(1);
        when(customerRepository.credit(eq("999999"), any(BigDecimal.class))).thenReturn(1);

        BigDecimal amount = new BigDecimal("1000.00");
        BankTransaction result = bankService.transfer("100001", "100002", amount, false);

        verify(customerRepository).debit("100001", new BigDecimal("1010.00"));
        verify(customerRepository).credit("100002", amount);
        verify(customerRepository).credit("999999", new BigDecimal("10.00"));
        assertEquals(TransactionStatus.SUCCESS, result.getStatus());
        assertEquals("Transfer completed; 1% fee: LKR 10.00", result.getDescription());
    }

    @Test
    void depositAndWithdrawalUpdateBalancesAndSaveHistoryRecords() {
        when(customerRepository.credit("100001", new BigDecimal("25.00"))).thenReturn(1);
        when(customerRepository.getByAccountNumber("100001"))
                .thenReturn(Optional.of(customer("100001")));
        when(customerRepository.debit("100001", new BigDecimal("10.00"))).thenReturn(1);

        BankTransaction deposit = bankService.deposit("100001", new BigDecimal("25.00"));
        BankTransaction withdrawal = bankService.withdraw("100001", new BigDecimal("10.00"));

        assertEquals("EXTERNAL", deposit.getFromAccount());
        assertEquals("100001", deposit.getToAccount());
        assertEquals("100001", withdrawal.getFromAccount());
        assertEquals("EXTERNAL", withdrawal.getToAccount());
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(any(BankTransaction.class));
    }

    @Test
    void insufficientWithdrawalDoesNotCreateSuccessRecord() {
        when(customerRepository.getByAccountNumber("100001"))
                .thenReturn(Optional.of(customer("100001")));
        when(customerRepository.debit("100001", new BigDecimal("50.00"))).thenReturn(0);

        assertThrows(InsufficientBalanceException.class,
                () -> bankService.withdraw("100001", new BigDecimal("50.00")));

        verify(transactionRepository, never()).save(any(BankTransaction.class));
        verify(auditService).recordFailure(
                "100001", "EXTERNAL", new BigDecimal("50.00"),
                "Insufficient balance in account 100001");
    }

    private Customer customer(String accountNumber) {
        return Customer.builder().accountNumber(accountNumber).build();
    }
}
