package Elramy.Group.MafroshartElramyz.services;



import Elramy.Group.MafroshartElramyz.enums.branch.BranchResponse;
import Elramy.Group.MafroshartElramyz.enums.branch.CreateBranchRequest;
import Elramy.Group.MafroshartElramyz.enums.branch.UpdateBranchRequest;

import java.util.List;

public interface BranchService {

    BranchResponse create(CreateBranchRequest request);

    BranchResponse update(Long id, UpdateBranchRequest request);

    BranchResponse getById(Long id);

    List<BranchResponse> getAll();

    void toggleStatus(Long id);

}