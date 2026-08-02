package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentRequest;
import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentResponse;

import java.util.List;

public interface StockAdjustmentService {

    StockAdjustmentResponse create(
            StockAdjustmentRequest request
    );

    StockAdjustmentResponse getById(Long id);

    List<StockAdjustmentResponse> getAll();

    List<StockAdjustmentResponse> getByProductStock(
            Long productStockId
    );

    List<StockAdjustmentResponse> getByBranch(
            Long branchId
    );
}