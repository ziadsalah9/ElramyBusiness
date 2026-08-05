//package Elramy.Group.MafroshartElramyz.services;
//
//
//import Elramy.Group.MafroshartElramyz.enums.product.CreateProductRequest;
//import Elramy.Group.MafroshartElramyz.enums.product.ProductResponse;
//import Elramy.Group.MafroshartElramyz.enums.product.UpdatePriceRequest;
//import Elramy.Group.MafroshartElramyz.enums.product.UpdateProductRequest;
//import Elramy.Group.MafroshartElramyz.exception.DuplicateProductException;
//import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
//import Elramy.Group.MafroshartElramyz.mapping.ProductMapper;
//import Elramy.Group.MafroshartElramyz.models.Product;
//import Elramy.Group.MafroshartElramyz.models.ProductPrice;
//import Elramy.Group.MafroshartElramyz.repository.ProductPriceRepository;
//import Elramy.Group.MafroshartElramyz.repository.ProductRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.util.List;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//public class ProductServiceImpl_old implements ProductService {
//
//    private final ProductRepository productRepository;
//    private final ProductPriceRepository productPriceRepository;
//    private final ProductMapper productMapper;
//
//    @Override
//    public ProductResponse create(CreateProductRequest request) {
//
//        //   validateCode(request.code());
//
//        String generatedCode = generateProductCode();
////        validateBarcode(request.barcode());
//        String finalBarcode;
//        if (request.barcode() != null && !request.barcode().isBlank()) {
//            validateBarcode(request.barcode());
//            finalBarcode = request.barcode();
//        } else {
//            finalBarcode = generateBarcode();
//        }
//
//        Product product = Product.builder()
//                .code(generatedCode)
//                .barcode(finalBarcode)
//                .name(request.name())
//                .model(request.model())
//                .itemType(request.itemType())
//                .color(request.color())
//                .size(request.size())
//                .minimumQuantity(request.minimumQuantity())
//                .active(true)
//                .build();
//
//        productRepository.save(product);
//
//        BigDecimal purchasePrice = request.purchasePrice();
//        BigDecimal sellingPrice = request.sellingPrice();
//        BigDecimal profitPercentage = request.profitPercentage();
//
//        if (purchasePrice != null && profitPercentage != null && sellingPrice == null) {
//            // Case 1: purchasePrice + profitPercentage -> calculate sellingPrice
//            sellingPrice = calculateSellingPrice(purchasePrice, profitPercentage);
//        } else if (purchasePrice != null && sellingPrice != null && profitPercentage == null) {
//            // Case 2: purchasePrice + sellingPrice -> calculate profitPercentage
//            profitPercentage = calculateProfitPercentage(purchasePrice, sellingPrice);
//        } else if (purchasePrice != null && sellingPrice != null && profitPercentage != null) {
//            // Case 3: Client provided all three -> keep provided values or re-verify
//            // Nothing to calculate
//        } else {
//            throw new IllegalArgumentException("Must provide purchasePrice along with either sellingPrice or profitPercentage.");
//        }
//
//
//        ProductPrice price = ProductPrice.builder()
//                .product(product)
//                .purchasePrice(purchasePrice)
//                .sellingPrice(sellingPrice)
//                .profitPercentage(profitPercentage)
//                .active(true)
//                .build();
//
//        productPriceRepository.save(price);
//
//        return productMapper.toResponse(product,price);
//    }
//
//    @Override
//    public ProductResponse update(Long id, UpdateProductRequest request) {
//
//        Product product = productRepository.findById(id)
//                .orElseThrow(() -> new ProductNotFoundException(id));
//
//        product.setName(request.name());
//        product.setModel(request.model());
//        product.setItemType(request.itemType());
//        product.setColor(request.color());
//        product.setSize(request.size());
//        product.setMinimumQuantity(request.minimumQuantity());
//
//        productRepository.save(product);
//
//        ProductPrice currentPrice = productPriceRepository
//                .findByProductIdAndActiveTrue(product.getId())
//                .orElse(null);
//
//        return productMapper.toResponse(product, currentPrice);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public ProductResponse getById(Long id) {
//        Product product = productRepository.findById(id)
//                .orElseThrow(() -> new ProductNotFoundException(id));
//
//        ProductPrice currentPrice = productPriceRepository
//                .findByProductIdAndActiveTrue(id)
//                .orElse(null);
//
//        return productMapper.toResponse(product, currentPrice);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<ProductResponse> getAll() {
//        return productRepository.findAll()
//                .stream()
//                .map(product -> {
//
//                    ProductPrice currentPrice = productPriceRepository
//                            .findByProductIdAndActiveTrue(product.getId())
//                            .orElse(null);
//
//                    return productMapper.toResponse(product, currentPrice);
//
//                })
//                .toList();
//    }
//
//  /*
//     * toggleStatus()
//     * search()
//     * updatePrice()
//     * calculateSellingPrice()
//     * calculateProfitPercentage()
//     * validateCode()
//     * validateBarcode()
//     *
//     */
//  @Override
//  public void toggleStatus(Long id) {
//
//      Product product = productRepository.findById(id)
//              .orElseThrow(() -> new ProductNotFoundException(id));
//
//      product.setActive(!product.getActive());
//
//      productRepository.save(product);
//  }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<ProductResponse> search(String keyword) {
//
//        return productRepository.search(keyword)
//                .stream()
//                .map(product -> {
//
//                    ProductPrice currentPrice = productPriceRepository
//                            .findByProductIdAndActiveTrue(product.getId())
//                            .orElse(null);
//
//                    return productMapper.toResponse(product, currentPrice);
//
//                })
//                .toList();
//    }
//
//    @Override
//    public ProductResponse updatePrice(Long id,
//                                       UpdatePriceRequest request) {
//
//        Product product = productRepository.findById(id)
//                .orElseThrow(() -> new ProductNotFoundException(id));
//
//        ProductPrice oldPrice = productPriceRepository
//                .findByProductIdAndActiveTrue(id)
//                .orElseThrow(() -> new RuntimeException("Current price not found"));
//
//        oldPrice.setActive(false);
//        productPriceRepository.save(oldPrice);
//        BigDecimal purchasePrice;
//        BigDecimal sellingPrice;
//        BigDecimal profitPercentage;
//
//        if (request.purchasePrice() != null
//                && request.profitPercentage() != null) {
//
//            purchasePrice = request.purchasePrice();
//            profitPercentage = request.profitPercentage();
//
//            sellingPrice = calculateSellingPrice(
//                    purchasePrice,
//                    profitPercentage);
//
//        } else if (request.purchasePrice() != null
//                && request.sellingPrice() != null) {
//
//            purchasePrice = request.purchasePrice();
//            sellingPrice = request.sellingPrice();
//
//            profitPercentage = calculateProfitPercentage(
//                    purchasePrice,
//                    sellingPrice);
//
//        } else {
//
//            throw new IllegalArgumentException(
//                    "Invalid price request.");
//
//        }
//
//        ProductPrice newPrice = ProductPrice.builder()
//                .product(product)
//                .purchasePrice(purchasePrice)
//                .sellingPrice(sellingPrice)
//                .profitPercentage(profitPercentage)
//                .active(true)
//                .build();
//
//        productPriceRepository.save(newPrice);
//
//        return productMapper.toResponse(product, newPrice);
//    }
//
///* ===========================================
//                Private Methods
//   =========================================== */
//
////    private void validateCode(String code) {
////
////        if (productRepository.existsByCode(code)) {
////            throw new DuplicateProductException("Product Code");
////        }
////    }
//
//    private String generateProductCode() {
//        String code;
//        do {
//            // شكل الكود: PRD- متبوع بأول 8 حروف من UUID
//            code = "PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
//        } while (productRepository.existsByCode(code)); // للتأكد التام من عدم التكرار
//
//        return code;
//    }
//
//    private String generateBarcode() {
//        String barcode;
//        do {
//            // توليد باركود يبدأ برقم عشوائي أو بادئة خاصة بالمحل (مثال: 200 + 9 أرقام عشوائية)
//            long randomNumber = (long) (Math.random() * 1_000_000_000L);
//            barcode = String.format("200%09d", randomNumber);
//        } while (productRepository.existsByBarcode(barcode));
//
//        return barcode;
//    }
//    private void validateBarcode(String barcode) {
//
//        if (barcode != null
//                && !barcode.isBlank()
//                && productRepository.existsByBarcode(barcode)) {
//
//            throw new DuplicateProductException("Barcode");
//        }
//    }
//
////    private BigDecimal calculateSellingPrice(
////            BigDecimal purchase,
////            BigDecimal profit) {
////
////        return purchase.add(
////                purchase.multiply(profit)
////                        .divide(BigDecimal.valueOf(100))
////        );
////    }
////
////    private BigDecimal calculateProfitPercentage(
////            BigDecimal purchase,
////            BigDecimal selling) {
////
////        return selling.subtract(purchase)
////                .multiply(BigDecimal.valueOf(100))
////                .divide(purchase, 2, RoundingMode.HALF_UP);
////    }
//
//    private BigDecimal calculateSellingPrice(BigDecimal purchase, BigDecimal profit) {
//        if (purchase == null || profit == null) return null;
//
//        BigDecimal profitAmount = purchase.multiply(profit)
//                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
//        return purchase.add(profitAmount);
//    }
//
//    private BigDecimal calculateProfitPercentage(BigDecimal purchase, BigDecimal selling) {
//        if (purchase == null || selling == null || purchase.compareTo(BigDecimal.ZERO) == 0) {
//            return BigDecimal.ZERO;
//        }
//
//        return selling.subtract(purchase)
//                .multiply(BigDecimal.valueOf(100))
//                .divide(purchase, 2, RoundingMode.HALF_UP);
//    }
//
//}