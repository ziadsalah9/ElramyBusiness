package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingRequest;
import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingResponse;
import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingSummary;
import Elramy.Group.MafroshartElramyz.enums.PaymentMethod;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.UnauthorizedException;
import Elramy.Group.MafroshartElramyz.mapping.CashClosingMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.CashClosing;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.CashClosingRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CashClosingServiceImpl implements CashClosingService {

    private final CashClosingRepository cashClosingRepository;
    private final BranchRepository branchRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final Elramy.Group.MafroshartElramyz.customeraccount.CustomerPaymentRepository paymentRepository;
    private final Elramy.Group.MafroshartElramyz.salesreturn.SalesReturnRepository returnRepository;
    private final CashClosingMapper cashClosingMapper;
    private final UserService userService;
    private final ActivityLogService activityLog;

    @Override
    public CashClosingResponse closeCash(CashClosingRequest request) {
        LocalDate closingDate = request.closingDate() != null ? request.closingDate() : LocalDate.now();

        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + request.branchId()));

        userService.validateBranchAccess(branch.getId());

        // Employees close today's cash once; correcting a saved closing or
        // closing another day is an ADMIN action (it can hide a shortfall).
        boolean admin = userService.getCurrentUser().getRole() == Role.ADMIN;
        if (!admin) {
            if (!closingDate.equals(LocalDate.now())) {
                throw new UnauthorizedException("يمكنك إغلاق كاش اليوم فقط.");
            }
            if (cashClosingRepository.findByBranchIdAndClosingDate(branch.getId(), closingDate).isPresent()) {
                throw new ConflictException("تم إغلاق كاش اليوم بالفعل. تعديل الإغلاق متاح للمدير فقط.");
            }
        }

        // Recalculate cash sales total for the date
        BigDecimal cashSalesTotal = calculateCashSalesTotal(branch.getId(), closingDate);

        // Calculate counted total
        long c5 = n(request.count5());
        long c10 = n(request.count10());
        long c20 = n(request.count20());
        long c50 = n(request.count50());
        long c100 = n(request.count100());
        long c200 = n(request.count200());

        BigDecimal countedTotal = BigDecimal.valueOf(c5 * 5L + c10 * 10L + c20 * 20L + c50 * 50L + c100 * 100L + c200 * 200L);
        BigDecimal difference = countedTotal.subtract(cashSalesTotal);

        // Upsert: if one exists for same branch+date, update; else create
        CashClosing closing = cashClosingRepository.findByBranchIdAndClosingDate(branch.getId(), closingDate)
                .orElseGet(() -> CashClosing.builder()
                        .branch(branch)
                        .closingDate(closingDate)
                        .cashSalesTotal(cashSalesTotal)
                        .build());

        boolean isNew = closing.getId() == null;

        String[] labels = {"فئة 5", "فئة 10", "فئة 20", "فئة 50", "فئة 100", "فئة 200"};

        Integer[] before = {closing.getCount5(), closing.getCount10(), closing.getCount20(),

                closing.getCount50(), closing.getCount100(), closing.getCount200()};

        long[] after = {c5, c10, c20, c50, c100, c200};

        Changes changes = Changes.of();

        for (int i = 0; i < labels.length; i++) {

            if (isNew) { if (after[i] > 0) changes.set(labels[i], after[i]); }

            else changes.add(labels[i], before[i], after[i]);

        }

        changes.add("المعدود", isNew ? null : closing.getCountedTotal(), countedTotal)

                .add("مبيعات الكاش", isNew ? null : closing.getCashSalesTotal(), cashSalesTotal)

                .add("الفرق", isNew ? null : closing.getDifference(), difference)

                .add("ملاحظات", isNew ? null : closing.getNotes(), request.notes());


        closing.setClosedBy(userService.getCurrentUser());
        closing.setCount5((int) c5);
        closing.setCount10((int) c10);
        closing.setCount20((int) c20);
        closing.setCount50((int) c50);
        closing.setCount100((int) c100);
        closing.setCount200((int) c200);
        closing.setCashSalesTotal(cashSalesTotal);
        closing.setCountedTotal(countedTotal);
        closing.setDifference(difference);
        closing.setNotes(request.notes());

        CashClosing saved = cashClosingRepository.save(closing);
        activityLog.record(ActivityEntity.CASH_CLOSING, isNew ? ActivityAction.CREATE : ActivityAction.UPDATE, saved.getId(),
                branch.getName() + " " + closingDate,
                (isNew ? "إغلاق كاش يوم " : "تعديل إغلاق كاش يوم ") + closingDate
                        + " (الفرق " + difference.stripTrailingZeros().toPlainString() + " ج.م)", changes);
        return cashClosingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CashClosingSummary calculateSummary(Long branchId, LocalDate closingDate) {
        LocalDate date = closingDate != null ? closingDate : LocalDate.now();
        userService.validateBranchAccess(branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + branchId));
        BigDecimal cashSalesTotal = calculateCashSalesTotal(branchId, date);
        // Empty counts → summary with zeros
        return new CashClosingSummary(branchId, branch.getName(), cashSalesTotal, BigDecimal.ZERO, BigDecimal.ZERO.subtract(cashSalesTotal) /* nothing counted yet: counted - expected */, 0,0,0,0,0,0);
    }

    @Override
    @Transactional(readOnly = true)
    public CashClosingResponse getByBranchAndDate(Long branchId, LocalDate closingDate) {
        LocalDate date = closingDate != null ? closingDate : LocalDate.now();
        userService.validateBranchAccess(branchId);
        return cashClosingMapper.toResponse(cashClosingRepository.findByBranchIdAndClosingDate(branchId, date)
                .orElseThrow(() -> new ResourceNotFoundException("No cash closing found for branch " + branchId + " on " + date)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashClosingResponse> getAllByBranch(Long branchId) {
        userService.validateBranchAccess(branchId);
        return cashClosingRepository.findByBranchIdOrderByClosingDateDesc(branchId).stream()
                .map(cashClosingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashClosingResponse> getAllByDate(LocalDate closingDate) {
        LocalDate date = closingDate != null ? closingDate : LocalDate.now();
        return cashClosingRepository.findByClosingDateOrderByBranchNameAsc(date).stream()
                .map(cashClosingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashClosingResponse> getAll() {
        return cashClosingRepository.findAll().stream().map(cashClosingMapper::toResponse).toList();
    }

    /**
     * Cash that should be in the drawer for the day: cash taken at sales
     * (the cash part of MIXED included), plus cash customer payments, minus
     * cash handed back for returns.
     */
    private BigDecimal calculateCashSalesTotal(Long branchId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return salesInvoiceRepository.sumCashByBranchBetween(branchId, from, to)
                .add(paymentRepository.sumByBranchAndMethodBetween(branchId, PaymentMethod.CASH, from, to))
                .subtract(returnRepository.sumCashRefundByBranchBetween(branchId, from, to));
    }

    private long n(Integer v) {
        return v == null ? 0L : Math.max(0L, v.longValue());
    }
}
