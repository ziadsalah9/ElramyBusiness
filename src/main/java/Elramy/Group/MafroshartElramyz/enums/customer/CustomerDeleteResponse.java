package Elramy.Group.MafroshartElramyz.enums.customer;

/**
 * Result of DELETE /api/customers/{id}.
 *
 * A customer with no sales invoices is removed permanently. One that has
 * invoices is deactivated instead, so the invoice history keeps pointing
 * at a real customer row.
 */
public record CustomerDeleteResponse(

        Long id,
        boolean permanentlyDeleted

) {
}
