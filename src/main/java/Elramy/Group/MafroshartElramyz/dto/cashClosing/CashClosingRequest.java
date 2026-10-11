package Elramy.Group.MafroshartElramyz.dto.cashClosing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CashClosingRequest(

        @NotNull
        Long branchId,

        LocalDate closingDate,

        @Min(0)
        Integer count5,

        @Min(0)
        Integer count10,

        @Min(0)
        Integer count20,

        @Min(0)
        Integer count50,

        @Min(0)
        Integer count100,

        @Min(0)
        Integer count200,

        String notes
) {
}
