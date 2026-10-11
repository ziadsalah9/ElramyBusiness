package Elramy.Group.MafroshartElramyz.dto.product;

/**
 * A colour/size entry in a product create/update. {@code id} is set for
 * an existing variant and null for a new one. Colour and size are each
 * optional and accept the stored value or the UI label.
 *
 * {@code quantity} is the wanted stock for this colour/size (null = leave
 * it as it is). For a new variant it is the opening stock; for an existing
 * one the difference is recorded as a stock adjustment in the ledger.
 *
 * {@code expectedQuantity} is the stock the form showed when it was
 * opened. If a sale or purchase changed it since, the edit is rejected
 * instead of silently undoing that sale.
 */
public record ProductVariantRequest(

        Long id,
        String color,
        String size,
        Integer quantity,
        Integer expectedQuantity

) {

    /** Colour/size only; the stock is left unchanged. */
    public ProductVariantRequest(Long id, String color, String size) {
        this(id, color, size, null, null);
    }

    public ProductVariantRequest(Long id, String color, String size, Integer quantity) {
        this(id, color, size, quantity, null);
    }
}
