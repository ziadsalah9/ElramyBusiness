package Elramy.Group.MafroshartElramyz.expense;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.expense.ExpenseDtos.*;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.services.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {

    @Test
    void expensesAreRoundedNumberedAndNeverInTheFuture() {
        ExpenseRepository expenses = mock(ExpenseRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(branches.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).name("فرع").build()));
        when(expenses.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ExpenseService service = new ExpenseService(expenses, branches, mock(UserService.class), mock(ActivityLogService.class));

        ExpenseResponse r = service.create(new ExpenseRequest(1L, ExpenseCategory.RENT, "إيجار أكتوبر", new BigDecimal("3000.005"),
                PaymentMethod.CASH, LocalDate.now()));
        assertEquals(0, new BigDecimal("3000.01").compareTo(r.amount()));
        assertTrue(r.expenseNumber().startsWith("EXP-"));

        assertThrows(IllegalArgumentException.class, () -> service.create(new ExpenseRequest(1L, ExpenseCategory.RENT, null,
                BigDecimal.TEN, PaymentMethod.CASH, LocalDate.now().plusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> service.create(new ExpenseRequest(1L, ExpenseCategory.RENT, null,
                BigDecimal.TEN, PaymentMethod.MIXED, LocalDate.now())));
    }
}
