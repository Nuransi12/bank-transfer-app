package com.example.bank_transfer_app.config;
import com.example.bank_transfer_app.model.Customer;
import com.example.bank_transfer_app.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) throws Exception {
        if (customerRepository.count() == 0) {
            customerRepository.save(Customer.builder()
                    .accountNumber("100001")
                    .fullName("Kamal Perera")
                    .balance(new BigDecimal("50000.00"))
                    .email("kamal@gmail.com")
                    .build());

            customerRepository.save(Customer.builder()
                    .accountNumber("100002")
                    .fullName("Nimali Silva")
                    .balance(new BigDecimal("25000.00"))
                    .email("nimali@gmail.com")
                    .build());

            customerRepository.save(Customer.builder()
                    .accountNumber("100003")
                    .fullName("Saman Fernando")
                    .balance(new BigDecimal("10000.00"))
                    .email("saman@gmail.com")
                    .build());
        }
    }
}