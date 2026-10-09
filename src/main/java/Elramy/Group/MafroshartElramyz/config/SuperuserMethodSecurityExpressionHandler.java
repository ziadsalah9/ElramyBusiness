package Elramy.Group.MafroshartElramyz.config;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionOperations;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.function.Supplier;

/**
 * Makes {@code ADMIN} a true superuser for every {@code @PreAuthorize}
 * expression, instead of merely happening to satisfy today's checks.
 *
 * <p>Before this, an admin could do everything only because every
 * existing annotation happened to list ADMIN. The moment somebody wrote
 * {@code @PreAuthorize("hasRole('SOME_NEW_ROLE')")} the administrator
 * would be locked out of that endpoint with no obvious cause. Here an
 * admin passes any role, authority or permission check by construction.
 *
 * <p>Implementation note: in Spring Security 7 the {@code hasRole} family
 * on {@code SecurityExpressionRoot} is {@code final}, so the root cannot be
 * subclassed. Instead this handler wraps the root that
 * {@code @EnableMethodSecurity} would normally install and short-circuits
 * the checks, delegating everything else to the real implementation.
 *
 * <p>{@code denyAll()} is deliberately NOT bypassed: it stays available as an
 * explicit way to lock a method down regardless of role.
 */
public class SuperuserMethodSecurityExpressionHandler
        extends DefaultMethodSecurityExpressionHandler {

    /**
     * The authority produced by {@code UserDetails.grantedAuthorities()}
     * for a user whose role is {@code ADMIN}.
     */
    public static final String SUPERUSER_AUTHORITY = "ROLE_ADMIN";


    @Override
    public EvaluationContext createEvaluationContext(
            Supplier<? extends Authentication> authentication,
            MethodInvocation mi) {

        EvaluationContext context =
                super.createEvaluationContext(
                        authentication,
                        mi
                );

        if (context instanceof StandardEvaluationContext standard) {

            Object root =
                    standard.getRootObject();

            if (root instanceof MethodSecurityExpressionOperations operations) {

                standard.setRootObject(
                        new SuperuserOperations(
                                operations,
                                isSuperuser(
                                        authentication.get()
                                )
                        )
                );
            }
        }

        return context;
    }


    private static boolean isSuperuser(
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(
                        SUPERUSER_AUTHORITY::equals
                );
    }


    /**
     * Delegates to the real expression root, but reports success for any
     * authority/role/permission check when the caller is an admin.
     */
    private record SuperuserOperations(
            MethodSecurityExpressionOperations delegate,
            boolean superuser)
            implements MethodSecurityExpressionOperations {

        @Override
        public boolean hasRole(String role) {
            return superuser || delegate.hasRole(role);
        }

        @Override
        public boolean hasAnyRole(String... roles) {
            return superuser || delegate.hasAnyRole(roles);
        }

        @Override
        public boolean hasAuthority(String authority) {
            return superuser || delegate.hasAuthority(authority);
        }

        @Override
        public boolean hasAnyAuthority(String... authorities) {
            return superuser || delegate.hasAnyAuthority(authorities);
        }

        @Override
        public boolean hasPermission(
                Object targetDomain,
                Object permission) {

            return superuser
                    || delegate.hasPermission(
                    targetDomain,
                    permission
            );
        }

        @Override
        public boolean hasPermission(
                Object targetId,
                String targetType,
                Object permission) {

            return superuser
                    || delegate.hasPermission(
                    targetId,
                    targetType,
                    permission
            );
        }

        // --------------------------------------------------
        // Everything below is passed straight through.
        // --------------------------------------------------

        @Override
        public Authentication getAuthentication() {
            return delegate.getAuthentication();
        }

        @Override
        public boolean permitAll() {
            return delegate.permitAll();
        }

        @Override
        public boolean denyAll() {
            return delegate.denyAll();
        }

        @Override
        public boolean isAnonymous() {
            return delegate.isAnonymous();
        }

        @Override
        public boolean isAuthenticated() {
            return delegate.isAuthenticated();
        }

        @Override
        public boolean isRememberMe() {
            return delegate.isRememberMe();
        }

        @Override
        public boolean isFullyAuthenticated() {
            return delegate.isFullyAuthenticated();
        }

        @Override
        public void setFilterObject(Object obj) {
            delegate.setFilterObject(obj);
        }

        @Override
        public Object getFilterObject() {
            return delegate.getFilterObject();
        }

        @Override
        public void setReturnObject(Object obj) {
            delegate.setReturnObject(obj);
        }

        @Override
        public Object getReturnObject() {
            return delegate.getReturnObject();
        }

        @Override
        public Object getThis() {
            return delegate.getThis();
        }
    }
}