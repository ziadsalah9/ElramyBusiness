package Elramy.Group.MafroshartElramyz.enums.branchTransfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBranchTransferRequest(

        @NotNull
        Long fromBranchId,

        @NotNull
        Long toBranchId,

        @NotEmpty
        List<@Valid BranchTransferItemRequest> items,

        String notes

) {
}