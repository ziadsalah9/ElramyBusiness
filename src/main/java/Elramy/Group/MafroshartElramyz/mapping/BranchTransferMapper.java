package Elramy.Group.MafroshartElramyz.mapping;


import Elramy.Group.MafroshartElramyz.enums.branchTransfer.BranchTransferItemResponse;
import Elramy.Group.MafroshartElramyz.enums.branchTransfer.BranchTransferResponse;
import Elramy.Group.MafroshartElramyz.models.BranchTransfer;
import Elramy.Group.MafroshartElramyz.models.BranchTransferItem;
import Elramy.Group.MafroshartElramyz.models.ProductStock;
import org.springframework.stereotype.Component;

@Component
public class BranchTransferMapper {

    public BranchTransferResponse toResponse(
            BranchTransfer transfer) {

        return new BranchTransferResponse(

                transfer.getId(),

                transfer.getFromBranch().getId(),
                transfer.getFromBranch().getName(),

                transfer.getToBranch().getId(),
                transfer.getToBranch().getName(),

                transfer.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList(),

                transfer.getCreatedBy() != null
                        ? transfer.getCreatedBy().getId()
                        : null,

                transfer.getCreatedBy() != null
                        ? transfer.getCreatedBy().getFullName()
                        : null,

                transfer.getNotes(),

                transfer.getCreatedAt()
        );
    }

    private BranchTransferItemResponse toItemResponse(
            BranchTransferItem item) {

        ProductStock stock = item.getProductStock();

        return new BranchTransferItemResponse(

                stock.getProduct().getId(),
                stock.getProduct().getCode(),
                stock.getProduct().getName(),

                item.getQuantity()
        );
    }
}