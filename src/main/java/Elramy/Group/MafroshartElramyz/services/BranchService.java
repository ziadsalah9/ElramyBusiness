package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.dto.branch.BranchResponse;

import java.util.List;

/**
 * Branches are read-only: the shop runs in single-branch mode (V7), so the
 * branch is used to scope invoices and closings, not managed in the app.
 */
public interface BranchService {

    BranchResponse getById(Long id);

    List<BranchResponse> getAll();
}
