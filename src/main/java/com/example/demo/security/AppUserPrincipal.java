package com.example.demo.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user stored in the SecurityContext, built when the JWT is verified.
 * Read it in controllers with @AuthenticationPrincipal.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final List<String> roles;

    public AppUserPrincipal(Long id, String email, List<String> roles) {
        this.id = id;
        this.email = email;
        this.roles = List.copyOf(roles);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    /** Role names as stored in the roles table, e.g. "admin". */
    public List<String> getRoles() {
        return roles;
    }

    // Required by UserDetails: the "username" in this app is the email
    @Override
    public String getUsername() {
        return email;
    }

    // Required by UserDetails, but the JWT flow never checks passwords here, so the hash isn't kept
    @Override
    public String getPassword() {
        return null;
    }

    // Uppercased with the "ROLE_" prefix so hasRole('ADMIN') matches "admin" in the roles table
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", email=" + email + ", roles=" + roles + "]";
    }
}
