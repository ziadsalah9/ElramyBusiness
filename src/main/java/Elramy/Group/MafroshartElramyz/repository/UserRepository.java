package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.models.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    // =========================================================
    // Authentication
    // =========================================================

    Optional<User> findByUsername(String username);

    // Fetches the branch eagerly so the login response can read
    // branchName without relying on an open session (see
    // spring.jpa.open-in-view=false).
    @EntityGraph(attributePaths = "branch")
    Optional<User> findWithBranchByUsername(String username);

    boolean existsByUsername(String username);


    // =========================================================
    // Users By Role
    // =========================================================

    List<User> findByRole(Role role);

    List<User> findByRoleAndActiveTrue(Role role);


    // =========================================================
    // Users By Branch
    // =========================================================

    List<User> findByBranchId(Long branchId);

    List<User> findByBranchIdAndActiveTrue(Long branchId);


    // =========================================================
    // Count Users
    // =========================================================

    long countByRole(Role role);

    long countByRoleAndActiveTrue(Role role);
}