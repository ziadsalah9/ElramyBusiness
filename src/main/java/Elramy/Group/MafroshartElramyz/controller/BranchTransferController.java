package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.branchTransfer.*;
import Elramy.Group.MafroshartElramyz.services.BranchTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branch-transfers")
@RequiredArgsConstructor
public class BranchTransferController {

    private final BranchTransferService branchTransferService;


    // =========================================================
    // CREATE TRANSFER
    // =========================================================

    @PostMapping
    public ResponseEntity<BranchTransferResponse> create(
            @Valid @RequestBody CreateBranchTransferRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(branchTransferService.create(request));
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<BranchTransferResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                branchTransferService.getById(id)
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    public ResponseEntity<List<BranchTransferResponse>> getAll() {

        return ResponseEntity.ok(
                branchTransferService.getAll()
        );
    }


    // =========================================================
    // GET TRANSFERS FROM BRANCH
    // =========================================================

    @GetMapping("/from-branch/{branchId}")
    public ResponseEntity<List<BranchTransferResponse>> getByFromBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                branchTransferService.getByFromBranch(branchId)
        );
    }


    // =========================================================
    // GET TRANSFERS TO BRANCH
    // =========================================================

    @GetMapping("/to-branch/{branchId}")
    public ResponseEntity<List<BranchTransferResponse>> getByToBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                branchTransferService.getByToBranch(branchId)
        );
    }
}