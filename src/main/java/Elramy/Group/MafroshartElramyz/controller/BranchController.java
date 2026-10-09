package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.dto.branch.BranchResponse;
import Elramy.Group.MafroshartElramyz.services.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only: single-branch mode, the app needs the branch only to scope data. */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class BranchController {

    private final BranchService branchService;

    @GetMapping("/{id}")
    public BranchResponse getById(@PathVariable Long id) {
        return branchService.getById(id);
    }

    @GetMapping
    public List<BranchResponse> getAll() {
        return branchService.getAll();
    }
}
