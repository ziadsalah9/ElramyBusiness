package Elramy.Group.MafroshartElramyz.enums.branchTransfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BranchTransferItemRequest(

        @NotNull
        Long productId,

        @NotNull
        @Min(1)
        Integer quantity

) {
}