package Elramy.Group.MafroshartElramyz.controller;

import Elramy.Group.MafroshartElramyz.dto.security.LoginRequest;
import Elramy.Group.MafroshartElramyz.dto.security.LoginResponse;
import Elramy.Group.MafroshartElramyz.dto.user.UserResponse;
import Elramy.Group.MafroshartElramyz.models.User;
import Elramy.Group.MafroshartElramyz.repository.UserRepository;
import Elramy.Group.MafroshartElramyz.services.JwtService;
import Elramy.Group.MafroshartElramyz.services.LoginAttemptService;
import Elramy.Group.MafroshartElramyz.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final LoginAttemptService loginAttempts;


    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // The direct peer address: X-Forwarded-For is client-controlled.
        String address = httpRequest.getRemoteAddr();
        loginAttempts.checkAllowed(request.username(), address);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException | DisabledException ex) {
            loginAttempts.loginFailed(request.username(), address);
            throw ex;
        }
        loginAttempts.loginSucceeded(request.username());


        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();


        String token =
                jwtService.generateToken(
                        userDetails
                );


        User user =
                userRepository
                        .findWithBranchByUsername(
                                request.username()
                        )
                        .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));


        return new LoginResponse(

                token,

                user.getId(),

                user.getUsername(),

                user.getFullName(),

                user.getRole(),

                user.getBranch() != null
                        ? user.getBranch().getId()
                        : null,

                user.getBranch() != null
                        ? user.getBranch().getName()
                        : null
        );
    }


    // =========================================================
    // CURRENT USER
    // =========================================================

    @GetMapping("/me")
    public UserResponse me() {

        return userService.getCurrentUserResponse();
    }
}