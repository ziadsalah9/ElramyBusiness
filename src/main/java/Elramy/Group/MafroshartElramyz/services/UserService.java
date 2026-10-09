package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.user.CreateUserRequest;
import Elramy.Group.MafroshartElramyz.enums.user.UpdateUserRequest;
import Elramy.Group.MafroshartElramyz.enums.user.UserResponse;
import Elramy.Group.MafroshartElramyz.models.User;

import java.util.List;

public interface UserService {

    // =========================================================
    // CREATE
    // =========================================================

    UserResponse create(
            CreateUserRequest request
    );

    // =========================================================
    // UPDATE
    // =========================================================

    UserResponse update(
            Long id,
            UpdateUserRequest request
    );

    // =========================================================
    // CURRENT USER (from JWT)
    // =========================================================

    UserResponse getCurrentUserResponse();


    // =========================================================
    // GET BY ID
    // =========================================================

    UserResponse getById(
            Long id
    );


    // =========================================================
    // GET ALL
    // =========================================================

    List<UserResponse> getAll();


    // =========================================================
    // GET BY USERNAME
    // =========================================================

    UserResponse getByUsername(
            String username
    );


    // =========================================================
    // GET EMPLOYEES
    // =========================================================

    List<UserResponse> getEmployees();


    // =========================================================
    // GET EMPLOYEES BY BRANCH
    // =========================================================

    List<UserResponse> getEmployeesByBranch(
            Long branchId
    );


    // =========================================================
    // ACTIVATE / DEACTIVATE
    // =========================================================

    UserResponse activate(
            Long id
    );

    UserResponse deactivate(
            Long id
    );
    User getCurrentUser();

     void validateBranchAccess(Long branchId);

    /**
     * Resolves the branch a read operation may see data for.
     *
     * <p>ADMIN may request any branch ({@code null} meaning "all
     * branches"). An EMPLOYEE is always limited to their own branch:
     * a mismatching {@code requestedBranchId} is rejected and the
     * employee's branch is returned regardless.
     */
    Long getEffectiveBranchId(Long requestedBranchId);
}