package Elramy.Group.MafroshartElramyz.expense;

import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(LocalDate from, LocalDate to);

    /** Cash expenses of a day - lower the cash closing's expected cash. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.branch.id = :branchId"
            + " AND e.paymentMethod = :method AND e.expenseDate = :day")
    BigDecimal sumByBranchMethodAndDay(@Param("branchId") Long branchId, @Param("method") PaymentMethod method,
                                       @Param("day") LocalDate day);

    long countByExpenseNumberStartingWith(String prefix);
}
