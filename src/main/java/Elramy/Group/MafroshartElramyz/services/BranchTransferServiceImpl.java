package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.TransactionType;
import Elramy.Group.MafroshartElramyz.enums.branchTransfer.*;
import Elramy.Group.MafroshartElramyz.exception.ProductNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.ResourceNotFoundException;
import Elramy.Group.MafroshartElramyz.mapping.BranchTransferMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.BranchTransfer;
import Elramy.Group.MafroshartElramyz.models.BranchTransferItem;
import Elramy.Group.MafroshartElramyz.models.ProductStock;
import Elramy.Group.MafroshartElramyz.models.StockTransaction;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.BranchTransferRepository;
import Elramy.Group.MafroshartElramyz.repository.ProductStockRepository;
import Elramy.Group.MafroshartElramyz.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchTransferServiceImpl
        implements BranchTransferService {

    private final BranchTransferRepository branchTransferRepository;
    private final BranchRepository branchRepository;
    private final ProductStockRepository productStockRepository;
    private final StockTransactionRepository stockTransactionRepository;

    private final BranchTransferMapper branchTransferMapper;

    private final UserService currentUser;


    // =========================================================
    // CREATE TRANSFER
    // =========================================================

    @Override
    public BranchTransferResponse create(
            CreateBranchTransferRequest request) {

        if (request.fromBranchId().equals(request.toBranchId())) {

            throw new IllegalArgumentException(
                    "Source branch and destination branch cannot be the same."
            );
        }

        Branch fromBranch = branchRepository
                .findById(request.fromBranchId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source branch not found."
                        )
                );

//        currentUser.validateBranchAccess(
//                fromBranch.getId()
//        );

        Branch toBranch = branchRepository
                .findById(request.toBranchId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Destination branch not found."
                        )
                );

        if (fromBranch.getId()
                .equals(toBranch.getId())) {

            throw new IllegalArgumentException(
                    "Source and destination branches cannot be the same"
            );
        }


        BranchTransfer transfer = BranchTransfer.builder()
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .notes(request.notes())
                .build();

        branchTransferRepository.save(transfer);
        List<BranchTransferItem> transferItems =
                new ArrayList<>();


        for (BranchTransferItemRequest itemRequest
                : request.items()) {

            ProductStock sourceStock =
                    productStockRepository
                            .findByProductIdAndBranchId(
                                    itemRequest.productId(),
                                    fromBranch.getId()
                            )
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Product stock not found in source branch."
                                    )
                            );


            // =================================================
            // CHECK STOCK
            // =================================================

            if (sourceStock.getQuantity()
                    < itemRequest.quantity()) {

                throw new RuntimeException(
                        "Insufficient stock for product: "
                                + sourceStock.getProduct().getName()
                );
            }


            // =================================================
            // GET / CREATE DESTINATION STOCK
            // =================================================

            ProductStock destinationStock =
                    productStockRepository
                            .findByProductIdAndBranchId(
                                    itemRequest.productId(),
                                    toBranch.getId()
                            )
                            .orElseGet(() -> {

                                ProductStock stock =
                                        ProductStock.builder()
                                                .product(
                                                        sourceStock.getProduct()
                                                )
                                                .branch(toBranch)
                                                .quantity(0)
                                                .minimumQuantity(
                                                        sourceStock
                                                                .getMinimumQuantity()
                                                )
                                                .build();

                                return productStockRepository.save(stock);
                            });


            // =================================================
            // DECREASE SOURCE
            // =================================================

            sourceStock.setQuantity(
                    sourceStock.getQuantity()
                            - itemRequest.quantity()
            );

            productStockRepository.save(sourceStock);


            // =================================================
            // INCREASE DESTINATION
            // =================================================

            destinationStock.setQuantity(
                    destinationStock.getQuantity()
                            + itemRequest.quantity()
            );

            productStockRepository.save(destinationStock);


            // =================================================
            // TRANSFER ITEM
            // =================================================

            BranchTransferItem transferItem =
                    BranchTransferItem.builder()
                            .transfer(transfer)
                            .productStock(sourceStock)
                            .quantity(itemRequest.quantity())
                            .build();

            transferItems.add(transferItem);


            // =================================================
            // TRANSFER OUT TRANSACTION
            // =================================================

            StockTransaction transferOut =
                    StockTransaction.builder()
                            .productStock(sourceStock)
                            .transactionType(
                                    TransactionType.TRANSFER_OUT
                            )
                            .quantity(itemRequest.quantity())
                            .referenceId(transfer.getId())
                            .referenceType("BRANCH_TRANSFER")
                            .notes(
                                    "Transfer to branch "
                                            + toBranch.getName()
                            )
                            .build();

            stockTransactionRepository.save(
                    transferOut
            );


            // =================================================
            // TRANSFER IN TRANSACTION
            // =================================================

            StockTransaction transferIn =
                    StockTransaction.builder()
                            .productStock(destinationStock)
                            .transactionType(
                                    TransactionType.TRANSFER_IN
                            )
                            .quantity(itemRequest.quantity())
                            .referenceId(transfer.getId())
                            .referenceType("BRANCH_TRANSFER")
                            .notes(
                                    "Transfer from branch "
                                            + fromBranch.getName()
                            )
                            .build();
            stockTransactionRepository.save(
                    transferIn
            );
        }


        transfer.setItems(transferItems);

        branchTransferRepository.save(transfer);


        return branchTransferMapper.toResponse(
                transfer
        );
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public BranchTransferResponse getById(Long id) {

        BranchTransfer transfer =
                branchTransferRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Branch transfer not found with id: "
                                                + id
                                )
                        );

        return branchTransferMapper.toResponse(
                transfer
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<BranchTransferResponse> getAll() {

        return branchTransferRepository.findAll()
                .stream()
                .map(branchTransferMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY FROM BRANCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<BranchTransferResponse> getByFromBranch(
            Long branchId) {

        return branchTransferRepository
                .findByFromBranchIdOrderByCreatedAtDesc(
                        branchId
                )
                .stream()
                .map(branchTransferMapper::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY TO BRANCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<BranchTransferResponse> getByToBranch(
            Long branchId) {

        return branchTransferRepository
                .findByToBranchIdOrderByCreatedAtDesc(
                        branchId
                )
                .stream()
                .map(branchTransferMapper::toResponse)
                .toList();
    }
}