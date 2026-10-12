package io.github.jeffromine.notes.config;

import io.github.jeffromine.notes.application.port.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Looks users up in the store. Admins (for {@code /api/admin/**}) are the usernames listed in {@code notes.admin-users}. */
@Service
public class StoreUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final Set<String> admins;

    public StoreUserDetailsService(UserRepository users, NotesProperties props) {
        this.users = users;
        this.admins = Set.copyOf(props.adminUsers());
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> User.withUsername(u.username())
                        .password(u.passwordHash())
                        .roles(admins.contains(u.username()) ? new String[] {"USER", "ADMIN"} : new String[] {"USER"})
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
