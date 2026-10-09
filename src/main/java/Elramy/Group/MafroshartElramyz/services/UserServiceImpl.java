package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.enums.Role;
import Elramy.Group.MafroshartElramyz.dto.user.CreateUserRequest;
import Elramy.Group.MafroshartElramyz.dto.user.UpdateUserRequest;
import Elramy.Group.MafroshartElramyz.dto.user.UserResponse;
import Elramy.Group.MafroshartElramyz.exception.BranchAccessDeniedException;
import Elramy.Group.MafroshartElramyz.exception.BranchNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.UnauthorizedException;
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
    private final ActivityLogService activityLog;


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

            throw new IllegalArgumentException(
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

        validatePassword(request.password());


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
                                    new BranchNotFoundException(
                                            request.branchId()
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
                        .credentialsChangedAt(java.time.LocalDateTime.now())
                        .branch(branch)
                        .active(true)
                        .build();


        // =====================================================
        // SAVE
        // =====================================================

        User savedUser =
                userRepository.save(user);

        // Never log the password itself.
        activityLog.record(ActivityEntity.USER, ActivityAction.CREATE, savedUser.getId(), userLabel(savedUser),
                "إضافة مستخدم «" + savedUser.getFullName() + "»",
                Changes.of().set("اسم المستخدم", savedUser.getUsername()).set("الاسم", savedUser.getFullName())
                        .set("الدور", savedUser.getRole())
                        .set("الفرع", savedUser.getBranch() != null ? savedUser.getBranch().getName() : null));


        // =====================================================
        // RESPONSE
        // =====================================================

        return toResponse(savedUser);
    }


    // =========================================================
    // UPDATE USER
    // =========================================================

    @Override
    public UserResponse update(
            Long id,
            UpdateUserRequest request) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new UserNotFoundException(id)
                        );

        // =====================================================
        // USERNAME
        // =====================================================

        String username = request.username().trim();

        if (!username.equals(user.getUsername())
                && userRepository.existsByUsername(username)) {

            throw new IllegalArgumentException(
                    "Username already exists: " + username
            );
        }

        // =====================================================
        // BRANCH (EMPLOYEE must belong to a branch, ADMIN must not)
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
                                    new BranchNotFoundException(
                                            request.branchId()
                                    )
                            );

        } else {
            // ADMIN is not scoped to a branch.
            branch = null;
        }

        // =====================================================
        // LAST-ADMIN GUARD
        // =====================================================
        // Demoting or deactivating the final active ADMIN would
        // permanently lock the system, so it is blocked.

        boolean demotingLastAdmin =
                Boolean.TRUE.equals(user.getActive())
                        && user.getRole() == Role.ADMIN
                        && (request.role() != Role.ADMIN
                        || Boolean.FALSE.equals(request.active()));

        // Same rule as deactivate(): an admin cannot lock themselves out
        // through the edit form (role change or "inactive").
        boolean self = user.getId().equals(getCurrentUser().getId());
        if (self && (request.role() != Role.ADMIN || Boolean.FALSE.equals(request.active()))) {
            throw new IllegalArgumentException("لا يمكنك إلغاء صلاحية المدير أو تعطيل حسابك بنفسك.");
        }

        if (demotingLastAdmin
                && countActiveAdmins() <= 1) {

            throw new IllegalArgumentException(
                    "Cannot remove the last active administrator"
            );
        }

        // =====================================================
        // PASSWORD POLICY
        // =====================================================

        if (request.password() != null
                && !request.password().isBlank()) {

            validatePassword(request.password());
        }

        // =====================================================
        // APPLY CHANGES
        // =====================================================

        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        Changes changes = Changes.of()
                .add("اسم المستخدم", user.getUsername(), username)
                .add("الاسم", user.getFullName(), request.fullName().trim())
                .add("الدور", user.getRole(), request.role())
                .add("الفرع", user.getBranch() != null ? user.getBranch().getName() : null, branch != null ? branch.getName() : null);
        if (request.active() != null) changes.add("نشط", user.getActive(), request.active());
        if (passwordChanged) changes.add("كلمة المرور", null, "تم تغييرها");

        // Old logins must stop working after a password change, and a freed
        // username must not let an old token act as its next owner.
        if (passwordChanged || !username.equals(user.getUsername())) {
            user.setCredentialsChangedAt(java.time.LocalDateTime.now());
        }
        user.setUsername(username);

        user.setFullName(request.fullName().trim());

        user.setRole(request.role());

        user.setBranch(branch);

        if (request.active() != null) {
            user.setActive(request.active());
        }

        // Password is only changed when a new one is supplied.
        if (request.password() != null
                && !request.password().isBlank()) {

            user.setPassword(
                    passwordEncoder.encode(
                            request.password()
                    )
            );
        }

        User saved = userRepository.save(user);
        if (!changes.isEmpty()) {
            activityLog.record(ActivityEntity.USER, ActivityAction.UPDATE, saved.getId(), userLabel(saved),
                    "تعديل مستخدم «" + saved.getFullName() + "»", changes);
        }
        return toResponse(saved);
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
                                new UserNotFoundException(id)
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
                                new UserNotFoundException(username)
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
                                new UserNotFoundException(id)
                        );

        user.setActive(true);

        User saved = userRepository.save(user);
        activityLog.record(ActivityEntity.USER, ActivityAction.STATUS, saved.getId(), userLabel(saved),
                "تفعيل مستخدم «" + saved.getFullName() + "»");
        return toResponse(saved);
    }


    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    public UserResponse deactivate(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new UserNotFoundException(id)
                        );

        // Self-deactivation is blocked: an admin must never
        // remove their own access by accident.
        if (user.getId().equals(getCurrentUser().getId())) {

            throw new IllegalArgumentException(
                    "You cannot deactivate your own account"
            );
        }

        // Last active ADMIN must remain.
        if (user.getRole() == Role.ADMIN
                && Boolean.TRUE.equals(user.getActive())
                && countActiveAdmins() <= 1) {

            throw new IllegalArgumentException(
                    "Cannot remove the last active administrator"
            );
        }

        user.setActive(false);

        User saved = userRepository.save(user);
        activityLog.record(ActivityEntity.USER, ActivityAction.STATUS, saved.getId(), userLabel(saved),
                "تعطيل مستخدم «" + saved.getFullName() + "»");
        return toResponse(saved);
    }

    // =====================================================
    // PASSWORD POLICY
    // =====================================================
    // Production requirement: at least 10 characters, with at
    // least one letter and one digit.

    private void validatePassword(String password) {

        if (password == null || password.length() < 10) {

            throw new IllegalArgumentException(
                    "Password must be at least 10 characters long"
            );
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);

        if (!hasLetter || !hasDigit) {

            throw new IllegalArgumentException(
                    "Password must contain at least one letter and one digit"
            );
        }
    }

    private long countActiveAdmins() {
        return userRepository.countByRoleAndActiveTrue(Role.ADMIN);
    }

    @Override
    public User getCurrentUser() {
        var  authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new UnauthorizedException(
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


    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserResponse() {

        return toResponse(
                getCurrentUser()
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


    public void validateBranchAccess(Long branchId) {

        User currentUser =
                getCurrentUser();



        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }


        if (currentUser.getBranch() == null) {

            throw new BranchAccessDeniedException(
                    "Current user is not assigned to a branch"
            );
        }


        if (!currentUser.getBranch()
                .getId()
                .equals(branchId)) {

            throw new BranchAccessDeniedException(
                    "You cannot access another branch"
            );
        }
    }


    @Override
    public Long getEffectiveBranchId(Long requestedBranchId) {

        User currentUser = getCurrentUser();

        if (currentUser.getRole() == Role.ADMIN) {
            return requestedBranchId;
        }

        // EMPLOYEE is always scoped to their own branch.
        if (requestedBranchId != null) {
            validateBranchAccess(requestedBranchId);
        }

        // null means "every branch", which only an ADMIN may get.
        if (currentUser.getBranch() == null) {
            throw new BranchAccessDeniedException("حسابك غير مرتبط بفرع. تواصل مع المدير.");
        }
        return currentUser.getBranch().getId();
    }

    private static String userLabel(User u) {
        return u.getFullName() + " (" + u.getUsername() + ")";
    }
}
