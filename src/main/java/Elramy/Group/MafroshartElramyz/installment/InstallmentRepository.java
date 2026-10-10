package Elramy.Group.MafroshartElramyz.installment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<InstallmentPlan, Long> {

    Optional<InstallmentPlan> findByInvoiceId(Long invoiceId);

    List<InstallmentPlan> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    /** Markup added to what the customer owes, over all their plans. */
    @Query("SELECT COALESCE(SUM(p.markupAmount), 0) FROM InstallmentPlan p WHERE p.customer.id = :customerId")
    BigDecimal sumMarkupByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(p.markupAmount), 0) FROM InstallmentPlan p")
    BigDecimal sumAllMarkup();

    /** A customer's unpaid installments, oldest due first (payments go there first). */
    @Query("SELECT i FROM Installment i WHERE i.plan.customer.id = :customerId AND i.paidAmount < i.amount"
            + " ORDER BY i.dueDate ASC, i.plan.id ASC, i.seq ASC")
    List<Installment> unpaidByCustomer(@Param("customerId") Long customerId);

    /** Every plan with its customer, invoice and schedule (one query), for the customers overview. */
    @Query("SELECT DISTINCT p FROM InstallmentPlan p JOIN FETCH p.customer JOIN FETCH p.invoice LEFT JOIN FETCH p.installments")
    List<InstallmentPlan> findAllWithSchedule();

    @Query("SELECT DISTINCT p FROM InstallmentPlan p JOIN FETCH p.customer JOIN FETCH p.invoice LEFT JOIN FETCH p.installments"
            + " WHERE p.customer.id = :customerId")
    List<InstallmentPlan> findByCustomerWithSchedule(@Param("customerId") Long customerId);

    /** Unpaid installments due on or before a day (overdue / coming up). */
    @Query("SELECT i FROM Installment i JOIN FETCH i.plan p JOIN FETCH p.customer JOIN FETCH p.invoice"
            + " WHERE i.paidAmount < i.amount AND i.dueDate <= :until ORDER BY i.dueDate ASC, p.id ASC, i.seq ASC")
    List<Installment> unpaidDueBy(@Param("until") LocalDate until);
}
