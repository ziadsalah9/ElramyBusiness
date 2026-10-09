package Elramy.Group.MafroshartElramyz.mapping;

import Elramy.Group.MafroshartElramyz.dto.product.ProductPriceResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductResponse;
import Elramy.Group.MafroshartElramyz.dto.product.ProductVariantResponse;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductVariant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {

        int quantity = product.totalQuantity();
        int minimum = product.getMinimumQuantity() != null ? product.getMinimumQuantity() : 0;

        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getModel(),
                product.getItemType(),
                toPriceResponse(product),
                product.getMinimumQuantity(),
                product.getActive(),
                quantity,
                quantity <= minimum,
                product.activeVariants().stream().map(ProductMapper::toVariantResponse).toList()
        );
    }

    public static ProductVariantResponse toVariantResponse(ProductVariant v) {
        return new ProductVariantResponse(
                v.getId(), v.getCode(), v.getBarcode(), v.getColor(), v.getSize(), v.label(),
                v.getQuantity(), v.getActive());
    }

    /**
     * The product's current price, in the same JSON shape the API always
     * returned (so clients are unaffected). Null when not priced yet.
     *
     * Cost (purchase price) and profit % are ADMIN-only: for anyone else
     * they are left out of the response, so they can't be read from the
     * API even where the screen doesn't show them. Every product response
     * goes through here, which keeps this rule in one place.
     */
    public ProductPriceResponse toPriceResponse(Product product) {

        if (product.getSellingPrice() == null) {
            return null;
        }

        boolean showCost = canSeeCost();
        return new ProductPriceResponse(
                product.getId(),
                product.getId(),
                showCost ? product.getPurchasePrice() : null,
                product.getSellingPrice(),
                showCost ? product.getProfitPercentage() : null,
                true,
                product.getPriceUpdatedAt()
        );
    }

    /**
     * True for an ADMIN. Also true when there is no authenticated user
     * (internal calls, tests): the restriction targets logged-in
     * non-admin users of the API.
     */
    static boolean canSeeCost() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Fail closed: no user means no cost price.
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
