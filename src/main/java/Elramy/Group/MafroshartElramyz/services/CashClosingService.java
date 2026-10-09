package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingRequest;
import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingResponse;
import Elramy.Group.MafroshartElramyz.enums.cashClosing.CashClosingSummary;

import java.time.LocalDate;
import java.util.List;

public interface CashClosingService {

    CashClosingResponse closeCash(CashClosingRequest request);

    CashClosingSummary calculateSummary(Long branchId, LocalDate closingDate);

    CashClosingResponse getByBranchAndDate(Long branchId, LocalDate closingDate);

    List<CashClosingResponse> getAllByBranch(Long branchId);

    List<CashClosingResponse> getAllByDate(LocalDate closingDate);

    List<CashClosingResponse> getAll();
}
