package Elramy.Group.MafroshartElramyz.enums.branchTransfer;

import java.time.LocalDateTime;
import java.util.List;

public record BranchTransferResponse(

        Long id,

        Long fromBranchId,
        String fromBranchName,

        Long toBranchId,
        String toBranchName,

        List<BranchTransferItemResponse> items,

        Long createdById,
        String createdByName,

        String notes,

        LocalDateTime createdAt

) {
}