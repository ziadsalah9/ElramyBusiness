package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.enums.user.CreateUserRequest;
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
}