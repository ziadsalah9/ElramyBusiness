package Elramy.Group.MafroshartElramyz.customeraccount;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.customeraccount.CustomerAccountDtos.*;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.CustomerRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** balance = unpaid on invoices - later payments - return credits; payments never exceed it. */
class CustomerAccountServiceTest {

    private CustomerPaymentRepository payments;
    private CustomerAccountService service;
    private Elramy.Group.MafroshartElramyz.installment.InstallmentService installments;

    @BeforeEach
    void setUp() {
        CustomerRepository customers = mock(CustomerRepository.class);
        payments = mock(CustomerPaymentRepository.class);
        SalesInvoiceRepository invoices = mock(SalesInvoiceRepository.class);
        SalesReturnRepository returns = mock(SalesReturnRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        Elramy.Group.MafroshartElramyz.installment.InstallmentRepository plans = mock(Elramy.Group.MafroshartElramyz.installment.InstallmentRepository.class);
        when(plans.sumMarkupByCustomer(3L)).thenReturn(new BigDecimal("40"));
        installments = mock(Elramy.Group.MafroshartElramyz.installment.InstallmentService.class);
        service = new CustomerAccountService(customers, payments, invoices, returns, branches, mock(UserService.class), mock(ActivityLogService.class), plans, installments);

        when(customers.findById(3L)).thenReturn(Optional.of(Customer.builder().id(3L).name("محمد").phone("010").build()));
        when(branches.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).name("فرع").build()));
        when(invoices.sumUnpaidByCustomer(3L)).thenReturn(new BigDecimal("500"));
        when(payments.sumByCustomer(3L)).thenReturn(new BigDecimal("150"));
        when(returns.sumCreditByCustomer(3L)).thenReturn(new BigDecimal("50"));
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void balanceIsUnpaidPlusInstallmentMarkupMinusPaymentsMinusReturnCredits() {
        assertEquals(0, new BigDecimal("340").compareTo(service.balance(3L)));   // 500 + 40 - 150 - 50
    }

    @Test
    void aPaymentLowersTheBalanceAndCannotExceedIt() {
        PaymentResponse r = service.recordPayment(3L, new PaymentRequest(1L, new BigDecimal("120"), PaymentMethod.CASH, null));
        assertEquals(0, new BigDecimal("220").compareTo(r.balanceAfter()));
        verify(installments).allocatePayment(eq(3L), eq(new BigDecimal("120.00")), any());
        assertTrue(r.paymentNumber().startsWith("PAY-"));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordPayment(3L, new PaymentRequest(1L, new BigDecimal("340.01"), PaymentMethod.CASH, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordPayment(3L, new PaymentRequest(1L, new BigDecimal("10"), PaymentMethod.MIXED, null)));
    }
}
