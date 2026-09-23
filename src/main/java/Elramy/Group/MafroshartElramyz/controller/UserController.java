package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.enums.user.CreateUserRequest;
import Elramy.Group.MafroshartElramyz.enums.user.UserResponse;
import Elramy.Group.MafroshartElramyz.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    // =========================================================
    // CREATE USER
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.create(request));
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                userService.getById(id)
        );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAll() {

        return ResponseEntity.ok(
                userService.getAll()
        );
    }


    // =========================================================
    // GET BY USERNAME
    // =========================================================

    @GetMapping("/username/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getByUsername(
            @PathVariable String username) {

        return ResponseEntity.ok(
                userService.getByUsername(username)
        );
    }


    // =========================================================
    // GET EMPLOYEES
    // =========================================================

    @GetMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getEmployees() {

        return ResponseEntity.ok(
                userService.getEmployees()
        );
    }


    // =========================================================
    // GET EMPLOYEES BY BRANCH
    // =========================================================

    @GetMapping("/employees/branch/{branchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getEmployeesByBranch(
            @PathVariable Long branchId) {

        return ResponseEntity.ok(
                userService.getEmployeesByBranch(branchId)
        );
    }


    // =========================================================
    // ACTIVATE
    // =========================================================

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                userService.activate(id)
        );
    }


    // =========================================================
    // DEACTIVATE
    // =========================================================

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                userService.deactivate(id)
        );
    }
}