package com.example.bank_transfer_app.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(unique = true, nullable = false)
    private String email;
}