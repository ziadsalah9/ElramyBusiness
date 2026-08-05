package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.product.*;
import Elramy.Group.MafroshartElramyz.exception.DuplicateProductException;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
import Elramy.Group.MafroshartElramyz.models.Product;
import Elramy.Group.MafroshartElramyz.models.ProductPrice;
import Elramy.Group.MafroshartElramyz.repository.ProductPriceRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductMapper productMapper;


    // =========================================================
    // CREATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse create(CreateProductRequest request) {

        String generatedCode = generateProductCode();

        String finalBarcode;

       finalBarcode = validateBarcode(generateBarcode());
        Product product = Product.builder()

                .code(generatedCode)

                .barcode(finalBarcode)

                .name(request.name())

                .model(request.model())

                .itemType(request.itemType())

                .color(request.color())

                .size(request.size())

                .minimumQuantity(
                        request.minimumQuantity() != null
                                ? request.minimumQuantity()
                                : 0
                )

                .active(true)

                .build();


        productRepository.save(product);


        /*
         * IMPORTANT:
         *
         * We DO NOT create ProductPrice here.
         *
         * The first purchase price will come
         * from the Purchase Invoice.
         */


        return productMapper.toResponse(
                product,
                null
        );
    }


    // =========================================================
    // UPDATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse update(
            Long id,
            UpdateProductRequest request) {

        Product product = productRepository.findById(id)

                .orElseThrow(
                        () -> new ProductNotFoundException(id)
                );


        product.setName(request.name());

        product.setModel(request.model());

        product.setItemType(request.itemType());

        product.setColor(request.color());

        product.setSize(request.size());

        product.setMinimumQuantity(
                request.minimumQuantity()
        );


        productRepository.save(product);


        ProductPrice currentPrice =
                productPriceRepository
                        .findByProductIdAndActiveTrue(id)
                        .orElse(null);


        return productMapper.toResponse(
                product,
                currentPrice
        );
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {

        Product product =
                productRepository.findById(id)

                        .orElseThrow(
                                () -> new ProductNotFoundException(id)
                        );


        ProductPrice currentPrice =
                productPriceRepository
                        .findByProductIdAndActiveTrue(id)
                        .orElse(null);


        return productMapper.toResponse(
                product,
                currentPrice
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {

        return productRepository.findAll()

                .stream()

                .map(product -> {

                    ProductPrice currentPrice =
                            productPriceRepository
                                    .findByProductIdAndActiveTrue(
                                            product.getId()
                                    )
                                    .orElse(null);


                    return productMapper.toResponse(
                            product,
                            currentPrice
                    );

                })

                .toList();
    }


    // =========================================================
    // TOGGLE STATUS
    // =========================================================

    @Override
    public void toggleStatus(Long id) {

        Product product =
                productRepository.findById(id)

                        .orElseThrow(
                                () -> new ProductNotFoundException(id)
                        );


        product.setActive(
                !product.getActive()
        );


        productRepository.save(product);
    }


    // =========================================================
    // SEARCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> search(
            String keyword) {

        return productRepository.search(keyword)

                .stream()

                .map(product -> {

                    ProductPrice currentPrice =
                            productPriceRepository
                                    .findByProductIdAndActiveTrue(
                                            product.getId()
                                    )
                                    .orElse(null);


                    return productMapper.toResponse(
                            product,
                            currentPrice
                    );

                })

                .toList();
    }




    @Override
    public ProductResponse createPrice(
            Long productId,
            CreateProductPriceRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );


        // Product must not already have a current price
        boolean priceExists =
                productPriceRepository
                        .findByProductIdAndActiveTrue(productId)
                        .isPresent();

        if (priceExists) {

            throw new IllegalArgumentException(
                    "Product already has a current price. " +
                            "Use update price instead."
            );
        }


        BigDecimal purchasePrice =
                request.purchasePrice();

        BigDecimal sellingPrice =
                request.sellingPrice();

        BigDecimal profitPercentage =
                request.profitPercentage();


        // =====================================================
        // PURCHASE PRICE IS REQUIRED
        // =====================================================

        if (purchasePrice == null) {

            throw new IllegalArgumentException(
                    "Purchase price is required."
            );
        }


        if (purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Purchase price must be greater than zero."
            );
        }


        // =====================================================
        // OPTION 1
        // purchasePrice + profitPercentage
        // => calculate sellingPrice
        // =====================================================

        if (profitPercentage != null
                && sellingPrice == null) {

            if (profitPercentage.compareTo(
                    BigDecimal.ZERO) < 0) {

                throw new IllegalArgumentException(
                        "Profit percentage cannot be negative."
                );
            }


            sellingPrice =
                    calculateSellingPrice(
                            purchasePrice,
                            profitPercentage
                    );
        }


        // =====================================================
        // OPTION 2
        // purchasePrice + sellingPrice
        // => calculate profitPercentage
        // =====================================================

        else if (sellingPrice != null
                && profitPercentage == null) {

            if (sellingPrice.compareTo(
                    BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "Selling price must be greater than zero."
                );
            }


            profitPercentage =
                    calculateProfitPercentage(
                            purchasePrice,
                            sellingPrice
                    );
        }


        // =====================================================
        // BOTH PROVIDED
        // =====================================================

        else if (sellingPrice != null
                && profitPercentage != null) {

            throw new IllegalArgumentException(
                    "Send either sellingPrice or profitPercentage, not both."
            );
        }


        // =====================================================
        // NONE PROVIDED
        // =====================================================

        else {

            throw new IllegalArgumentException(
                    "You must provide either sellingPrice " +
                            "or profitPercentage."
            );
        }


        // =====================================================
        // CREATE PRODUCT PRICE
        // =====================================================

        ProductPrice price =
                ProductPrice.builder()
                        .product(product)
                        .purchasePrice(purchasePrice)
                        .sellingPrice(sellingPrice)
                        .profitPercentage(profitPercentage)
                        .active(true)
                        .build();


        productPriceRepository.save(price);


        return productMapper.toResponse(
                product,
                price
        );
    }


    // =========================================================
    // UPDATE PRICE
    // =========================================================

    @Override
    public ProductResponse updatePrice(Long id,
                                       UpdatePriceRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        ProductPrice oldPrice = productPriceRepository
                .findByProductIdAndActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Current price not found"));

        oldPrice.setActive(false);
        productPriceRepository.save(oldPrice);
        BigDecimal purchasePrice;
        BigDecimal sellingPrice;
        BigDecimal profitPercentage;

        if (request.purchasePrice() != null
                && request.profitPercentage() != null) {

            purchasePrice = request.purchasePrice();
            profitPercentage = request.profitPercentage();

            sellingPrice = calculateSellingPrice(
                    purchasePrice,
                    profitPercentage);

        } else if (request.purchasePrice() != null
                && request.sellingPrice() != null) {

            purchasePrice = request.purchasePrice();
            sellingPrice = request.sellingPrice();

            profitPercentage = calculateProfitPercentage(
                    purchasePrice,
                    sellingPrice);

        } else {

            throw new IllegalArgumentException(
                    "Invalid price request.");

        }

        ProductPrice newPrice = ProductPrice.builder()
                .product(product)
                .purchasePrice(purchasePrice)
                .sellingPrice(sellingPrice)
                .profitPercentage(profitPercentage)
                .active(true)
                .build();

        productPriceRepository.save(newPrice);

        return productMapper.toResponse(product, newPrice);
    }
    // =========================================================
    // PRIVATE METHODS
    // =========================================================

    private String generateProductCode() {

        String code;

        do {

            code =
                    "PRD-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

        } while (
                productRepository.existsByCode(code)
        );


        return code;
    }


    private String generateBarcode() {

        String barcode;

        do {

            long randomNumber =
                    (long) (
                            Math.random()
                                    * 1_000_000_000L
                    );


            barcode =
                    String.format(
                            "200%09d",
                            randomNumber
                    );

        } while (
                productRepository.existsByBarcode(barcode)
        );


        return barcode;
    }


    private String validateBarcode(
            String barcode) {

        if (barcode != null
                && !barcode.isBlank()
                && productRepository.existsByBarcode(
                        barcode
                )) {

            throw new DuplicateProductException(
                    "Barcode"
            );
        }
        return barcode;
    }


    private BigDecimal calculateSellingPrice(
            BigDecimal purchase,
            BigDecimal profit) {

        return purchase

                .add(
                        purchase
                                .multiply(profit)
                                .divide(
                                        BigDecimal.valueOf(100),
                                        2,
                                        RoundingMode.HALF_UP
                                )
                )

                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    private BigDecimal calculateProfitPercentage(
            BigDecimal purchase,
            BigDecimal selling) {

        if (purchase.compareTo(BigDecimal.ZERO) == 0) {

            throw new IllegalArgumentException(
                    "Purchase price cannot be zero"
            );
        }


        return selling

                .subtract(purchase)

                .multiply(
                        BigDecimal.valueOf(100)
                )

                .divide(
                        purchase,
                        2,
                        RoundingMode.HALF_UP
                );
    }
}