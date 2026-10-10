package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.CashClosing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long> {

    Optional<CashClosing> findByBranchIdAndClosingDate(Long branchId, LocalDate closingDate);

    List<CashClosing> findByBranchIdOrderByClosingDateDesc(Long branchId);

    List<CashClosing> findByClosingDateOrderByBranchNameAsc(LocalDate closingDate);

    List<CashClosing> findByClosingDateBetweenOrderByClosingDateAsc(LocalDate from, LocalDate to);
}
