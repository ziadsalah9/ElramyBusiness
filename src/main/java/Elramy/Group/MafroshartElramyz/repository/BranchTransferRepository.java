package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.BranchTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchTransferRepository
        extends JpaRepository<BranchTransfer, Long> {

    List<BranchTransfer> findByFromBranchIdOrderByCreatedAtDesc(
            Long branchId
    );

    List<BranchTransfer> findByToBranchIdOrderByCreatedAtDesc(
            Long branchId
    );

    List<BranchTransfer> findByCreatedByIdOrderByCreatedAtDesc(
            Long userId
    );
}