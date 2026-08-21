package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentRequest;
import Elramy.Group.MafroshartElramyz.enums.stock.StockAdjustmentResponse;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.StockAdjustmentMapper;
import Elramy.Group.MafroshartElramyz.models.ProductStock;
import Elramy.Group.MafroshartElramyz.models.StockAdjustment;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.ProductStockRepository;
import Elramy.Group.MafroshartElramyz.repository.StockAdjustmentRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StockAdjustmentServiceImpl
        implements StockAdjustmentService {

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ProductStockRepository productStockRepository;
    private final StockTransactionRepository stockTransactionRepository;

    private final StockAdjustmentMapper stockAdjustmentMapper;

    private final UserService currentUserService;


    // =========================================================
    // CREATE ADJUSTMENT
    // =========================================================

    @Override
    public StockAdjustmentResponse create(
            StockAdjustmentRequest request) {

        ProductStock stock = productStockRepository
                .findById(request.productStockId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product stock not found with id: "
                                        + request.productStockId()
                        )
                );

        currentUserService.validateBranchAccess(
                stock.getBranch().getId()
        );
        User currentUser =
                currentUserService.getCurrentUser();

        Integer systemQuantity = stock.getQuantity();

        Integer actualQuantity = request.actualQuantity();

        Integer difference =
                actualQuantity - systemQuantity;


        // =====================================================
        // UPDATE STOCK
        // =====================================================

        stock.setQuantity(actualQuantity);


        productStockRepository.save(stock);


        // =====================================================
        // CREATE ADJUSTMENT
        // =====================================================

        StockAdjustment adjustment =
                StockAdjustment.builder()
                        .productStock(stock)
                        .systemQuantity(systemQuantity)
                        .actualQuantity(actualQuantity)
                        .difference(difference)
                        .notes(request.notes())
                        .createdBy(currentUser)
                        .build();

        stockAdjustmentRepository.save(adjustment);


        // =====================================================
        // CREATE TRANSACTION
        // =====================================================

        if (difference != 0) {

            TransactionType transactionType =
                    difference > 0
                            ? TransactionType .ADJUSTMENT_IN
                            : TransactionType.ADJUSTMENT_OUT;

            StockTransaction transaction =
                    StockTransaction.builder()
                            .productStock(stock)
                            .transactionType(transactionType)
                            .quantity(Math.abs(difference))
                            .referenceId(adjustment.getId())
                            .referenceType("STOCK_ADJUSTMENT")
                            .notes(request.notes())
                            .createdBy(currentUser)
                            .build();

            stockTransactionRepository.save(transaction);
        }

        return stockAdjustmentMapper.toResponse(adjustment);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public StockAdjustmentResponse getById(Long id) {

        StockAdjustment adjustment =
                stockAdjustmentRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Stock adjustment not found with id: "
                                                + id
                                )
                        );

        return stockAdjustmentMapper.toResponse(adjustment);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<StockAdjustmentResponse> getAll() {

        return stockAdjustmentRepository.findAll()
                .stream()
                .map(stockAdjustmentMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY PRODUCT STOCK
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<StockAdjustmentResponse> getByProductStock(
            Long productStockId) {

        return stockAdjustmentRepository
                .findByProductStockIdOrderByCreatedAtDesc(
                        productStockId
                )
                .stream()
                .map(stockAdjustmentMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY BRANCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<StockAdjustmentResponse> getByBranch(
            Long branchId) {

        return stockAdjustmentRepository
                .findByProductStockBranchIdOrderByCreatedAtDesc(
                        branchId
                )
                .stream()
                .map(stockAdjustmentMapper::toResponse)
                .toList();
    }
}