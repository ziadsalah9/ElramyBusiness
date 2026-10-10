package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingRequest;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.UnauthorizedException;
import Elramy.Group.MafroshartElramyz.mapping.CashClosingMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.CashClosing;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.CashClosingRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Employees close today's cash once; corrections and past days are ADMIN-only. */
class CashClosingServiceImplTest {

    private CashClosingRepository closings;
    private UserService users;
    private CashClosingServiceImpl service;
    private Elramy.Group.MafroshartElramyz.reports.ReportService reports;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        closings = mock(CashClosingRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        SalesInvoiceRepository sales = mock(SalesInvoiceRepository.class);
        users = mock(UserService.class);
        Elramy.Group.MafroshartElramyz.customeraccount.CustomerPaymentRepository payments = mock(Elramy.Group.MafroshartElramyz.customeraccount.CustomerPaymentRepository.class);
        Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository returns = mock(Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository.class);
        when(sales.sumCashByBranchBetween(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(payments.sumByBranchAndMethodBetween(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(returns.sumCashRefundByBranchBetween(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        Elramy.Group.MafroshartElramyz.expense.ExpenseRepository expenses = mock(Elramy.Group.MafroshartElramyz.expense.ExpenseRepository.class);
        Elramy.Group.MafroshartElramyz.supplier.SupplierPaymentRepository supplierPays = mock(Elramy.Group.MafroshartElramyz.supplier.SupplierPaymentRepository.class);
        when(expenses.sumByBranchMethodAndDay(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(supplierPays.sumByBranchAndMethodBetween(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        reports = mock(Elramy.Group.MafroshartElramyz.reports.ReportService.class);
        service = new CashClosingServiceImpl(closings, branches, sales, payments, returns, expenses, supplierPays, mock(CashClosingMapper.class), users, mock(ActivityLogService.class), reports);

        Branch branch = Branch.builder().id(1L).name("فرع كوم حماة").build();
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(closings.findByBranchIdAndClosingDate(anyLong(), any())).thenReturn(Optional.empty());
        when(closings.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void loginAs(Role role) {
        User u = new User();
        u.setId(5L);
        u.setRole(role);
        when(users.getCurrentUser()).thenReturn(u);
    }

    private static CashClosingRequest request(LocalDate date) {
        return new CashClosingRequest(1L, date, 0, 0, 0, 0, 1, 0, null);
    }

    @Test
    void employeeClosesTodayOnce() {
        loginAs(Role.EMPLOYEE);
        assertDoesNotThrow(() -> service.closeCash(request(today)));

        when(closings.findByBranchIdAndClosingDate(1L, today)).thenReturn(Optional.of(CashClosing.builder().id(3L).build()));
        assertThrows(ConflictException.class, () -> service.closeCash(request(today)), "no overwriting a saved closing");
    }

    @Test
    void employeeCannotCloseAnotherDay() {
        loginAs(Role.EMPLOYEE);
        assertThrows(UnauthorizedException.class, () -> service.closeCash(request(today.minusDays(7))));
        verify(closings, never()).save(any());
    }

    @Test
    void adminCanCorrectAPastClosing() {
        loginAs(Role.ADMIN);
        CashClosing old = CashClosing.builder().id(3L).closingDate(today.minusDays(7)).build();
        when(closings.findByBranchIdAndClosingDate(1L, today.minusDays(7))).thenReturn(Optional.of(old));

        service.closeCash(request(today.minusDays(7)));
        assertEquals(0, new BigDecimal("100").compareTo(old.getCountedTotal()));
    }

    @Test
    void theDaysProfitIsShownToAdminsOnly() {
        when(reports.summary(today, today)).thenReturn(new Elramy.Group.MafroshartElramyz.reports.ReportDtos.Summary(
                3, new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500"), new BigDecimal("300"),
                new BigDecimal("200"), new BigDecimal("40"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, new BigDecimal("50"), new BigDecimal("150")));

        loginAs(Role.ADMIN);
        var admin = service.calculateSummary(1L, today).dayProfit();
        assertEquals(0, new BigDecimal("150").compareTo(admin.netProfit()));
        assertEquals(0, new BigDecimal("300").compareTo(admin.costOfSales()));

        loginAs(Role.EMPLOYEE);
        assertNull(service.calculateSummary(1L, today).dayProfit(), "employees never see costs or profit");
    }
}
