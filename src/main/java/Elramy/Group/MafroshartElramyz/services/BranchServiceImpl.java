package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.dto.branch.BranchResponse;
import Elramy.Group.MafroshartElramyz.exception.BranchNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Override
    public BranchResponse getById(Long id) {
        return map(branchRepository.findById(id).orElseThrow(() -> new BranchNotFoundException(id)));
    }

    @Override
    public List<BranchResponse> getAll() {
        return branchRepository.findAll().stream().map(BranchServiceImpl::map).toList();
    }

    private static BranchResponse map(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.getPhone(), branch.getActive());
    }
}
