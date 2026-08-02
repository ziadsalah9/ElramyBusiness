package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.enums.stock.StockTransactionResponse;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import org.springframework.stereotype.Component;

@Component
public class StockTransactionMapper {

    public StockTransactionResponse toResponse(
            StockTransaction transaction) {

        var stock = transaction.getProductStock();
        var product = stock.getProduct();
        var branch = stock.getBranch();

        return new StockTransactionResponse(

                transaction.getId(),

                stock.getId(),

                product.getId(),
                product.getName(),

                branch.getId(),
                branch.getName(),

                transaction.getTransactionType(),

                transaction.getQuantity(),

                transaction.getUnitPrice(),

                transaction.getReferenceId(),

                transaction.getNotes(),

                transaction.getCreatedBy() != null
                        ? transaction.getCreatedBy().getId()
                        : null,

                transaction.getCreatedBy() != null
                        ? transaction.getCreatedBy().getFullName()
                        : null,

                transaction.getCreatedAt()
        );
    }
}