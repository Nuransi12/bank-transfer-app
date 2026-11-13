package com.example.bank_transfer_app.repository;
import com.example.bank_transfer_app.model.BankTransaction;
import com.example.bank_transfer_app.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. All transactions, newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC")
    List<BankTransaction> findAllLatest();

    // 2. Account history (sender OR receiver)
    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :acc OR t.toAccount = :acc ORDER BY t.createdAt DESC")
    List<BankTransaction> findHistory(@Param("acc") String acc);

    // 3. Filter by status
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. Count by status
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT t FROM BankTransaction t WHERE t.amount > :min AND t.status = :status " +
            "AND t.fromAccount <> :externalAccount AND t.toAccount <> :externalAccount " +
            "ORDER BY t.amount DESC")
    List<BankTransaction> findLargeTransfers(
            @Param("min") BigDecimal min,
            @Param("status") TransactionStatus status,
            @Param("externalAccount") String externalAccount);

    @Query("SELECT SUM(t.amount) FROM BankTransaction t " +
            "WHERE t.fromAccount = :account AND t.status = :status AND t.toAccount <> :externalAccount")
    BigDecimal getTotalSentByAccount(
            @Param("account") String account,
            @Param("status") TransactionStatus status,
            @Param("externalAccount") String externalAccount);
}