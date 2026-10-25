package com.capstone.steam.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.user.UserRepository;

/** Usato solo al login (AuthenticationManager). Le altre richieste si autenticano tramite JWT. */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        return userRepository.findByLogin(login.trim())
                .map(AppUserPrincipal::fromUser)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
