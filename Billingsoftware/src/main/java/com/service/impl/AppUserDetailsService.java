package com.service.impl;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.entity.UserEntity;
import com.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Email not found for the email: " + email));

        String role = existingUser.getRole().toUpperCase();
        if (role.endsWith("_ROLE")) {
            role = role.substring(0, role.length() - 5);
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        return new User(
                existingUser.getEmail(),
                existingUser.getPassword(),
                Collections.singleton(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
    }
}
