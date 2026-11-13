package com.example.bank_transfer_app.controller;
import com.example.bank_transfer_app.dto.TransferRequest;
import com.example.bank_transfer_app.dto.MoneyRequest;
import com.example.bank_transfer_app.model.Customer;
import com.example.bank_transfer_app.service.BankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("customers", bankService.getAllCustomers(keyword));
        model.addAttribute("totalBalance", bankService.getTotalBankBalance());
        model.addAttribute("successCount", bankService.getSuccessCount());
        model.addAttribute("failedCount", bankService.getFailedCount());
        model.addAttribute("latestTransactions", bankService.getLatestTransactions());
        model.addAttribute("transferRequest", new TransferRequest());
        model.addAttribute("depositRequest", new MoneyRequest());
        model.addAttribute("withdrawRequest", new MoneyRequest());
        model.addAttribute("newCustomer", new Customer());
        model.addAttribute("keyword", keyword);
        return "bank";
    }

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam("min") BigDecimal min, Model model) {
        model.addAttribute("min", min);
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        return "large-transfers";
    }

    @PostMapping("/transfer")
    public String handleTransfer(@Valid @ModelAttribute("transferRequest") TransferRequest request,
                                 BindingResult result,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (result.hasErrors()) {
            return dashboard(null, model);
        }
        try {
            bankService.transfer(request.getFromAccount(), request.getToAccount(), request.getAmount(), request.isSimulateFailure());
            redirectAttributes.addFlashAttribute("successMessage", "Transfer completed successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage() + " (All changes were rolled back)");
        }
        return "redirect:/";
    }

    @PostMapping("/deposit")
    public String handleDeposit(@Valid @ModelAttribute("depositRequest") MoneyRequest request,
                                BindingResult result,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (result.hasErrors()) {
            model.addAttribute("errorMessage", result.getAllErrors().get(0).getDefaultMessage());
            return dashboard(null, model);
        }
        try {
            bankService.deposit(request.getAccountNumber(), request.getAmount());
            redirectAttributes.addFlashAttribute("successMessage", "Deposit completed successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/withdraw")
    public String handleWithdraw(@Valid @ModelAttribute("withdrawRequest") MoneyRequest request,
                                 BindingResult result,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (result.hasErrors()) {
            model.addAttribute("errorMessage", result.getAllErrors().get(0).getDefaultMessage());
            return dashboard(null, model);
        }
        try {
            bankService.withdraw(request.getAccountNumber(), request.getAmount());
            redirectAttributes.addFlashAttribute("successMessage", "Withdrawal completed successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("newCustomer") Customer customer,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        if (result.hasErrors()) {
            return dashboard(null, model);
        }
        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("successMessage", "Customer added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable String accountNumber, Model model) {
        model.addAttribute("customer", bankService.getCustomerByAccount(accountNumber));
        model.addAttribute("history", bankService.getAccountHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSentByAccount(accountNumber));
        return "history";
    }
}