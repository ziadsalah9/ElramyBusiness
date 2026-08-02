package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.enums.branchTransfer.*;

import java.util.List;

public interface BranchTransferService {

    BranchTransferResponse create(
            CreateBranchTransferRequest request
    );

    BranchTransferResponse getById(Long id);

    List<BranchTransferResponse> getAll();

    List<BranchTransferResponse> getByFromBranch(
            Long branchId
    );

    List<BranchTransferResponse> getByToBranch(
            Long branchId
    );
}