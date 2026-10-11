package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import Elramy.Group.MafroshartElramyz.repository.ProductVariantRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class StockServiceImpl implements StockService {

    private final ProductVariantRepository variantRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final UserService currentUserService;
    private final EntityManager entityManager;


    @Override
    public void increaseStock(
            Long variantId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        validateQuantity(quantity);

        ProductVariant variant = lockVariant(variantId);
        variant.setQuantity(variant.getQuantity() + quantity);
        variantRepository.save(variant);

        createTransaction(variant, transactionType, quantity, unitPrice, referenceId, referenceType, notes);
    }


    @Override
    public void decreaseStock(
            Long variantId,
            Integer quantity,
            TransactionType transactionType,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        validateQuantity(quantity);

        // PESSIMISTIC lock: two concurrent sales of the same variant
        // serialize on this row instead of both reading the same quantity
        // and letting one of them oversell.
        ProductVariant variant = lockVariant(variantId);

        if (variant.getQuantity() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient stock for " + variant.getProduct().getName() + " (" + variant.label() + ")."
                            + " Available: " + variant.getQuantity()
                            + ", requested: " + quantity
            );
        }

        variant.setQuantity(variant.getQuantity() - quantity);
        variantRepository.save(variant);

        createTransaction(variant, transactionType, quantity, unitPrice, referenceId, referenceType, notes);
    }


    /**
     * Locks the row and re-reads it. A plain locking query is not enough:
     * when the caller already loaded the variant (invoices and the product
     * form do), Hibernate returns that cached instance with the quantity read
     * BEFORE the lock, and two concurrent sales would both start from the
     * same stock. Pending edits are flushed first so the refresh keeps them.
     */
    private ProductVariant lockVariant(Long variantId) {
        ProductVariant variant = entityManager.find(ProductVariant.class, variantId);
        if (variant == null) {
            throw new ResourceNotFoundException("Product variant " + variantId + " not found.");
        }
        entityManager.flush();
        entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        return variant;
    }

    private void createTransaction(
            ProductVariant variant,
            TransactionType transactionType,
            Integer quantity,
            BigDecimal unitPrice,
            Long referenceId,
            String referenceType,
            String notes) {

        // Quantities are stored positive; direction lives in transactionType.
        stockTransactionRepository.save(
                StockTransaction.builder()
                        .product(variant.getProduct())
                        .variant(variant)
                        .transactionType(transactionType)
                        .quantity(Math.abs(quantity))
                        .unitPrice(unitPrice)
                        .referenceId(referenceId)
                        .referenceType(referenceType)
                        .notes(notes)
                        .createdBy(currentUserService.getCurrentUser())
                        .build());
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
    }
}
