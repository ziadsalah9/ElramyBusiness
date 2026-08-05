package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.enums.user.CreateUserRequest;
import Elramy.Group.MafroshartElramyz.enums.user.UserResponse;
import Elramy.Group.MafroshartElramyz.exception.UserNotFoundException;
import Elramy.Group.MafroshartElramyz.models.Branch;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.BranchRepository;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;


    // =========================================================
    // CREATE USER
    // =========================================================

    @Override
    public UserResponse create(
            CreateUserRequest request) {

        // =====================================================
        // CHECK USERNAME
        // =====================================================

        if (userRepository.existsByUsername(
                request.username())) {

            throw new RuntimeException(
                    "Username already exists: "
                            + request.username()
            );
        }


        // =====================================================
        // CHECK ROLE
        // =====================================================

        if (request.role() == null) {

            throw new IllegalArgumentException(
                    "Role is required"
            );
        }


        // =====================================================
        // CHECK ADMIN LIMIT
        // =====================================================

        if (request.role() == Role.ADMIN) {

            long adminCount =
                    userRepository.countByRoleAndActiveTrue(
                            Role.ADMIN
                    );

            if (adminCount >= 3) {

                throw new IllegalArgumentException(
                        "Maximum number of active admins is 3"
                );
            }
        }


        // =====================================================
        // CHECK EMPLOYEE LIMIT
        // =====================================================

        if (request.role() == Role.EMPLOYEE) {

            long employeeCount =
                    userRepository.countByRoleAndActiveTrue(
                            Role.EMPLOYEE
                    );

            if (employeeCount >= 10) {

                throw new IllegalArgumentException(
                        "Maximum number of active employees is 10"
                );
            }
        }


        // =====================================================
        // BRANCH
        // =====================================================

        Branch branch = null;

        if (request.role() == Role.EMPLOYEE) {

            if (request.branchId() == null) {

                throw new IllegalArgumentException(
                        "Employee must be assigned to a branch"
                );
            }

            branch =
                    branchRepository
                            .findById(request.branchId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Branch not found with id: "
                                                    + request.branchId()
                                    )
                            );
        }

        // ADMIN doesn't need a branch.
        // branch remains null.


        // =====================================================
        // CREATE USER
        // =====================================================

        User user =
                User.builder()
                        .username(
                                request.username().trim()
                        )
                        .password(
                                passwordEncoder.encode(
                                        request.password()
                                )
                        )
                        .fullName(
                                request.fullName().trim()
                        )
                        .role(request.role())
                        .branch(branch)
                        .active(true)
                        .build();


        // =====================================================
        // SAVE
        // =====================================================

        User savedUser =
                userRepository.save(user);


        // =====================================================
        // RESPONSE
        // =====================================================

        return toResponse(savedUser);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found with id: "
                                                + id
                                )
                        );

        return toResponse(user);
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {

        return userRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // =========================================================
    // GET BY USERNAME
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByUsername(
            String username) {

        User user =
                userRepository.findByUsername(username)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found with username: "
                                                + username
                                )
                        );

        return toResponse(user);
    }


    // =========================================================
    // GET EMPLOYEES
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getEmployees() {

        return userRepository
                .findByRole(Role.EMPLOYEE)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // =========================================================
    // GET EMPLOYEES BY BRANCH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getEmployeesByBranch(
            Long branchId) {

        return userRepository
                .findByBranchId(branchId)
                .stream()
                .filter(user ->
                        user.getRole() == Role.EMPLOYEE
                )
                .map(this::toResponse)
                .toList();
    }


    // =========================================================
    // ACTIVATE
    // =========================================================

    @Override
    public UserResponse activate(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found with id: "
                                                + id
                                )
                        );

        user.setActive(true);

        return toResponse(
                userRepository.save(user)
        );
    }


    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    public UserResponse deactivate(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found with id: "
                                                + id
                                )
                        );

        user.setActive(false);

        return toResponse(
                userRepository.save(user)
        );
    }

    @Override
    public User getCurrentUser() {
        var  authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        var username = authentication.getName();
        return userRepository.findByUsername(username
        ).orElseThrow(() ->
                new UserNotFoundException(
                        username
                )
        );
    }


    // =========================================================
    // MAPPER
    // =========================================================

    private UserResponse toResponse(
            User user) {

        return new UserResponse(

                user.getId(),

                user.getUsername(),

                user.getFullName(),

                user.getRole(),

                user.getBranch() != null
                        ? user.getBranch().getId()
                        : null,

                user.getBranch() != null
                        ? user.getBranch().getName()
                        : null,

                user.getActive()
        );
    }
}