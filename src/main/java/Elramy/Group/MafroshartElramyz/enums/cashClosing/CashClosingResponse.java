package Elramy.Group.MafroshartElramyz.enums.cashClosing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CashClosingResponse(

        Long id,

        Long branchId,
        String branchName,

        Long closedById,
        String closedByName,

        LocalDate closingDate,

        Integer count5,
        Integer count10,
        Integer count20,
        Integer count50,
        Integer count100,
        Integer count200,

        BigDecimal cashSalesTotal,
        BigDecimal countedTotal,
        BigDecimal difference,

        String notes,

        LocalDateTime createdAt
) {
}
