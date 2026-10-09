package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingResponse;
import Elramy.Group.MafroshartElramyz.dto.cashClosing.CashClosingSummary;
import Elramy.Group.MafroshartElramyz.models.CashClosing;
import org.springframework.stereotype.Component;

@Component
public class CashClosingMapper {

    public CashClosingResponse toResponse(CashClosing closing) {
        return new CashClosingResponse(
                closing.getId(),
                closing.getBranch().getId(),
                closing.getBranch().getName(),
                closing.getClosedBy() != null ? closing.getClosedBy().getId() : null,
                closing.getClosedBy() != null ? closing.getClosedBy().getFullName() : null,
                closing.getClosingDate(),
                closing.getCount5(),
                closing.getCount10(),
                closing.getCount20(),
                closing.getCount50(),
                closing.getCount100(),
                closing.getCount200(),
                closing.getCashSalesTotal(),
                closing.getCountedTotal(),
                closing.getDifference(),
                closing.getNotes(),
                closing.getCreatedAt()
        );
    }

    public CashClosingSummary toSummary(CashClosing closing) {
        return new CashClosingSummary(
                closing.getBranch().getId(),
                closing.getBranch().getName(),
                closing.getCashSalesTotal(),
                closing.getCountedTotal(),
                closing.getDifference(),
                closing.getCount5(),
                closing.getCount10(),
                closing.getCount20(),
                closing.getCount50(),
                closing.getCount100(),
                closing.getCount200()
        );
    }
}
