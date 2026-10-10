package Elramy.Group.MafroshartElramyz.dashboard;

import Elramy.Group.MafroshartElramyz.models.SalesInvoice;
import Elramy.Group.MafroshartElramyz.repository.CustomerRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Counts and today's figures for the home screen in one small request,
 * instead of downloading every product, customer and invoice. Employees
 * get their branch's sales and no purchase count.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class DashboardController {

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SalesInvoiceRepository sales;
    private final PurchaseInvoiceRepository purchases;
    private final UserService userService;
    private final Elramy.Group.MafroshartElramyz.services.CashClosingService cashClosings;
    private final Elramy.Group.MafroshartElramyz.repository.CashClosingRepository closingRepository;
    private final Elramy.Group.MafroshartElramyz.repository.BranchRepository branches;
    private final Elramy.Group.MafroshartElramyz.installment.InstallmentRepository installments;

    public record RecentSale(Long id, String invoiceNumber, String customerName, BigDecimal total, BigDecimal remaining, LocalDateTime createdAt) {
    }

    public record DaySales(LocalDate date, int count, BigDecimal total) {
    }

    /**
     * {@code todayProfit}: ADMIN only (null for employees, who never see
     * costs). {@code expectedCash}: what should be in the drawer now.
     * {@code unclosedYesterday}: yesterday had sales but no cash closing.
     * {@code dueInstallments*}: unpaid installments due today or earlier.
     */
    public record Summary(long products, long customers, long salesInvoices, Long purchaseInvoices,
                          int todaySalesCount, BigDecimal todaySalesTotal, List<RecentSale> recentSales,
                          BigDecimal todayProfit, BigDecimal expectedCash, boolean cashClosedToday, boolean unclosedYesterday,
                          int dueInstallmentsCount, BigDecimal dueInstallmentsAmount, List<DaySales> last7Days) {
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public Summary summary() {
        Long branchId = userService.getEffectiveBranchId(null);   // null = admin, every branch
        boolean admin = branchId == null;
        LocalDateTime today = LocalDate.now().atStartOfDay();
        List<SalesInvoice> todays = sales.findInRange(branchId, today, today.plusDays(1));
        List<SalesInvoice> recent = admin ? sales.findTop5ByOrderByCreatedAtDesc() : sales.findTop5ByBranchIdOrderByCreatedAtDesc(branchId);

        // The drawer: the employee's branch, or the shop's main (first) branch for an admin.
        LocalDate day = LocalDate.now();
        Long cashBranch = branchId != null ? branchId
                : branches.findAll().stream().map(b -> b.getId()).min(Long::compare).orElse(null);
        BigDecimal expectedCash = BigDecimal.ZERO;
        BigDecimal todayProfit = null;
        boolean closedToday = false, unclosedYesterday = false;
        if (cashBranch != null) {
            var cash = cashClosings.calculateSummary(cashBranch, day);    // day profit only for admins
            expectedCash = cash.cashSalesTotal();
            todayProfit = cash.dayProfit() == null ? null : cash.dayProfit().netProfit();
            closedToday = closingRepository.findByBranchIdAndClosingDate(cashBranch, day).isPresent();
            LocalDateTime yesterday = today.minusDays(1);
            unclosedYesterday = !sales.findInRange(cashBranch, yesterday, today).isEmpty()
                    && closingRepository.findByBranchIdAndClosingDate(cashBranch, day.minusDays(1)).isEmpty();
        }

        var due = installments.unpaidDueBy(day);
        BigDecimal dueAmount = due.stream().map(i -> i.remaining()).reduce(BigDecimal.ZERO, BigDecimal::add);

        java.util.Map<LocalDate, List<SalesInvoice>> week = new java.util.TreeMap<>();
        for (int k = 6; k >= 0; k--) week.put(day.minusDays(k), new java.util.ArrayList<>());
        for (SalesInvoice inv : sales.findInRange(branchId, today.minusDays(6), today.plusDays(1))) {
            week.computeIfAbsent(inv.getCreatedAt().toLocalDate(), k -> new java.util.ArrayList<>()).add(inv);
        }
        List<DaySales> last7 = week.entrySet().stream().map(e -> new DaySales(e.getKey(), e.getValue().size(),
                e.getValue().stream().map(SalesInvoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();

        return new Summary(
                products.countByActiveTrue(),
                customers.countByActiveTrue(),
                admin ? sales.count() : sales.countByBranchId(branchId),
                admin ? purchases.count() : null,
                todays.size(),
                todays.stream().map(SalesInvoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                recent.stream().map(i -> new RecentSale(i.getId(), i.getInvoiceNumber(),
                        i.getCustomer() != null ? i.getCustomer().getName() : null, i.getTotal(),
                        i.getTotal().subtract(i.getPaid()), i.getCreatedAt())).toList(),
                todayProfit, expectedCash, closedToday, unclosedYesterday, due.size(), dueAmount, last7);
    }
}
