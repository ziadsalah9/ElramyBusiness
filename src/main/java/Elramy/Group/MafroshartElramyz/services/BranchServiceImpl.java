package Elramy.Group.MafroshartElramyz.services;


import Elramy.Group.MafroshartElramyz.enums.branch.BranchResponse;
import Elramy.Group.MafroshartElramyz.enums.branch.CreateBranchRequest;
import Elramy.Group.MafroshartElramyz.enums.branch.UpdateBranchRequest;
import Elramy.Group.MafroshartElramyz.exception.BranchNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.DuplicateBranchException;
import Elramy.Group.MafroshartElramyz.mapping.BranchMapper;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    @Override
    public BranchResponse create(CreateBranchRequest request) {

        if (branchRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateBranchException(request.name());
        }

        Branch branch = Branch.builder()
                .name(request.name())
                .address(request.address())
                .phone(request.phone())
                .active(true)
                .build();

        branchRepository.save(branch);

        return map(branch);
    }

    @Override
    public BranchResponse update(Long id, UpdateBranchRequest request) {

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() ->
                        new BranchNotFoundException(id));

        if (!branch.getName().equalsIgnoreCase(request.name())
                && branchRepository.existsByNameIgnoreCase(request.name())) {

            throw new DuplicateBranchException(request.name());
        }

        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setPhone(request.phone());

        branchRepository.save(branch);

        return map(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getById(Long id) {

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() ->
                        new BranchNotFoundException(id));

        return map(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getAll() {

        return branchRepository.findAll()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public void toggleStatus(Long id) {

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() ->
                        new BranchNotFoundException(id));

        branch.setActive(!branch.getActive());

        branchRepository.save(branch);
    }

    private BranchResponse map(Branch branch) {

        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getActive()
        );
    }

}