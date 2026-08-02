package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.branch.BranchResponse;
import Elramy.Group.MafroshartElramyz.enums.branch.CreateBranchRequest;
import Elramy.Group.MafroshartElramyz.enums.branch.UpdateBranchRequest;
import Elramy.Group.MafroshartElramyz.services.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    public ResponseEntity<BranchResponse> create(
            @Valid @RequestBody CreateBranchRequest request){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BranchResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchRequest request){

        return ResponseEntity.ok(
                branchService.update(id,request)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchResponse> getById(
            @PathVariable Long id){

        return ResponseEntity.ok(
                branchService.getById(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<BranchResponse>> getAll(){

        return ResponseEntity.ok(
                branchService.getAll()
        );
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<Void> toggleStatus(
            @PathVariable Long id){

        branchService.toggleStatus(id);

        return ResponseEntity.noContent().build();
    }

}