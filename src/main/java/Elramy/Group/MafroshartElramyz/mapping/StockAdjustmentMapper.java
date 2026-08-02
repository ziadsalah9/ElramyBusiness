package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentResponse;
import Elramy.Group.MafroshartElramyz.models.StockAdjustment;
import org.springframework.stereotype.Component;

@Component
public class StockAdjustmentMapper {

    public StockAdjustmentResponse toResponse(
            StockAdjustment adjustment) {

        var stock = adjustment.getProductStock();
        var product = stock.getProduct();
        var branch = stock.getBranch();

        return new StockAdjustmentResponse(

                adjustment.getId(),

                stock.getId(),

                product.getId(),
                product.getName(),

                branch.getId(),
                branch.getName(),

                adjustment.getSystemQuantity(),
                adjustment.getActualQuantity(),
                adjustment.getDifference(),

                adjustment.getNotes(),

                adjustment.getCreatedBy() != null
                        ? adjustment.getCreatedBy().getId()
                        : null,

                adjustment.getCreatedBy() != null
                        ? adjustment.getCreatedBy().getFullName()
                        : null,

                adjustment.getCreatedAt()
        );
    }
}