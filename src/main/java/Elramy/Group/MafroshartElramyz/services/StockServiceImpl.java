package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.stock.StockResponse;
import Elramy.Group.MafroshartElramyz.enums.stock.StockTransactionResponse;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.ProductStockMapper;
import Elramy.Group.MafroshartElramyz.mapping.StockTransactionMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductStock;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductStockRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StockServiceImpl implements StockService {

    private final ProductStockRepository productStockRepository;
    private final StockTransactionRepository stockTransactionRepository;

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;

    private final ProductStockMapper productStockMapper;
    private final StockTransactionMapper stockTransactionMapper;
    private final UserService currentUserService;


    // =========================================================
    // GET STOCK
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public StockResponse getById(Long id) {

        ProductStock stock = productStockRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock not found with id: " + id
                        )
                );

        return productStockMapper.toResponse(stock);
    }


    @Override
    @Transactional(readOnly = true)
    public StockResponse getByProductAndBranch(
            Long productId,
            Long branchId) {

        ProductStock stock = productStockRepository
                .findByProductIdAndBranchId(productId, branchId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock not found for product: "
                                        + productId
                                        + " and branch: "
                                        + branchId
                        )
                );

        return productStockMapper.toResponse(stock);
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockResponse> getAll() {

        return productStockRepository.findAll()
                .stream()
                .map(productStockMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockResponse> getByProduct(Long productId) {

        return productStockRepository.findByProductId(productId)
                .stream()
                .map(productStockMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockResponse> getByBranch(Long branchId) {

        return productStockRepository.findByBranchId(branchId)
                .stream()
                .map(productStockMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockResponse> getLowStock() {

        return productStockRepository.findLowStock()
                .stream()
                .map(productStockMapper::toResponse)
                .toList();
    }


    // =========================================================
    // INCREASE STOCK
    // =========================================================

    @Override
    public void increaseStock(
            Long productId,
            Long branchId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        //currentUserService.validateBranchAccess(branchId);


        validateQuantity(quantity);

        ProductStock stock = getOrCreateStock(
                productId,
                branchId
        );

        stock.setQuantity(
                stock.getQuantity() + quantity
        );

        productStockRepository.save(stock);

        createTransaction(
                stock,
                transactionType,
                quantity,
                unitPrice,
                referenceId,
                referenceType,
                notes
        );
    }


    // =========================================================
    // DECREASE STOCK
    // =========================================================

    @Override
    public void decreaseStock(
            Long productId,
            Long branchId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        //currentUserService.validateBranchAccess(branchId);

        validateQuantity(quantity);

        ProductStock stock = productStockRepository
                .findByProductIdAndBranchId(productId, branchId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock not found for product: "
                                        + productId
                                        + " and branch: "
                                        + branchId
                        )
                );

        if (stock.getQuantity() < quantity) {

            throw new RuntimeException(
                    "Insufficient stock. Available: "
                            + stock.getQuantity()
                            + ", requested: "
                            + quantity
            );
        }

        stock.setQuantity(
                stock.getQuantity() - quantity
        );

        productStockRepository.save(stock);

        createTransaction(
                stock,
                transactionType,
                -quantity,
                unitPrice,
                referenceId,
                referenceType,
                notes
        );
    }


    // =========================================================
    // TRANSACTIONS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<StockTransactionResponse> getTransactions(
            Long productStockId) {

        return stockTransactionRepository
                .findByProductStockIdOrderByCreatedAtDesc(productStockId)
                .stream()
                .map(stockTransactionMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockTransactionResponse> getTransactionsByProduct(
            Long productId) {

        return stockTransactionRepository
                .findByProductStockProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(stockTransactionMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<StockTransactionResponse> getTransactionsByBranch(
            Long branchId) {

        return stockTransactionRepository
                .findByProductStockBranchIdOrderByCreatedAtDesc(branchId)
                .stream()
                .map(stockTransactionMapper::toResponse)
                .toList();
    }


    // =========================================================
    // PRIVATE METHODS
    // =========================================================

    private ProductStock getOrCreateStock(
            Long productId,
            Long branchId) {

        return productStockRepository
                .findByProductIdAndBranchId(productId, branchId)
                .orElseGet(() -> {

                    Product product = productRepository
                            .findById(productId)
                            .orElseThrow(() ->
                                    new ProductNotFoundException(productId)
                            );

                    Branch branch = branchRepository
                            .findById(branchId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Branch not found with id: "
                                                    + branchId
                                    )
                            );

                    ProductStock stock = ProductStock.builder()
                            .product(product)
                            .branch(branch)
                            .quantity(0)
                            .minimumQuantity(0)
                            .build();

                    return productStockRepository.save(stock);
                });
    }


    private void createTransaction(
            ProductStock stock,
            TransactionType transactionType,
            Integer quantity,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        StockTransaction transaction =
                StockTransaction.builder()
                        .productStock(stock)
                        .transactionType(transactionType)
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .referenceId(referenceId)
                        .referenceType(referenceType)
                        .notes(notes)
                        .build();

        stockTransactionRepository.save(transaction);
    }


    private void validateQuantity(Integer quantity) {

        if (quantity == null || quantity <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }
    }
}