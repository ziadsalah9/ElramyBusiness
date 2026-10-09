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

    public record RecentSale(Long id, String invoiceNumber, String customerName, BigDecimal total, BigDecimal remaining, LocalDateTime createdAt) {
    }

    public record Summary(long products, long customers, long salesInvoices, Long purchaseInvoices,
                          int todaySalesCount, BigDecimal todaySalesTotal, List<RecentSale> recentSales) {
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public Summary summary() {
        Long branchId = userService.getEffectiveBranchId(null);   // null = admin, every branch
        boolean admin = branchId == null;
        LocalDateTime today = LocalDate.now().atStartOfDay();
        List<SalesInvoice> todays = sales.findInRange(branchId, today, today.plusDays(1));
        List<SalesInvoice> recent = admin ? sales.findTop5ByOrderByCreatedAtDesc() : sales.findTop5ByBranchIdOrderByCreatedAtDesc(branchId);
        return new Summary(
                products.countByActiveTrue(),
                customers.countByActiveTrue(),
                admin ? sales.count() : sales.countByBranchId(branchId),
                admin ? purchases.count() : null,
                todays.size(),
                todays.stream().map(SalesInvoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                recent.stream().map(i -> new RecentSale(i.getId(), i.getInvoiceNumber(),
                        i.getCustomer() != null ? i.getCustomer().getName() : null, i.getTotal(),
                        i.getTotal().subtract(i.getPaid()), i.getCreatedAt())).toList());
    }
}
