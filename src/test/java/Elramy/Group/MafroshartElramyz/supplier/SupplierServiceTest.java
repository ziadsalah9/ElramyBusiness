package Elramy.Group.MafroshartElramyz.supplier;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.PurchaseInvoiceRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import Elramy.Group.MafroshartElramyz.supplier.SupplierDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** What we owe a supplier = unpaid purchases - later payments; payments never exceed it. */
class SupplierServiceTest {

    private SupplierRepository suppliers;
    private SupplierService service;
    private Supplier acme;

    @BeforeEach
    void setUp() {
        suppliers = mock(SupplierRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        PurchaseInvoiceRepository purchases = mock(PurchaseInvoiceRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        service = new SupplierService(suppliers, payments, purchases, branches, mock(UserService.class), mock(ActivityLogService.class));

        acme = Supplier.builder().id(4L).name("مصنع النسيج").active(true).build();
        when(suppliers.findById(4L)).thenReturn(Optional.of(acme));
        when(branches.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).name("فرع").build()));
        when(purchases.sumUnpaidBySupplier(4L)).thenReturn(new BigDecimal("1000"));
        when(payments.sumBySupplier(4L)).thenReturn(new BigDecimal("400"));
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(suppliers.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void balanceAndPayments() {
        assertEquals(0, new BigDecimal("600").compareTo(service.balance(4L)));
        PaymentResponse r = service.pay(4L, new PaymentRequest(1L, new BigDecimal("250"), PaymentMethod.CASH, null));
        assertEquals(0, new BigDecimal("350").compareTo(r.balanceAfter()));
        assertTrue(r.paymentNumber().startsWith("SPY-"));
        assertThrows(IllegalArgumentException.class, () -> service.pay(4L, new PaymentRequest(1L, new BigDecimal("600.01"), PaymentMethod.CASH, null)));
        assertThrows(IllegalArgumentException.class, () -> service.pay(4L, new PaymentRequest(1L, BigDecimal.TEN, PaymentMethod.MIXED, null)));
    }

    @Test
    void namesAreUniqueAndStoppedSuppliersCannotBeUsed() {
        when(suppliers.nameTaken("مصنع النسيج", null)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(new SupplierRequest(" مصنع النسيج ", null, null, null)));

        acme.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.activeSupplier(4L));
    }
}
