package com.capstone.steam.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer ADMIN al arrancar (ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD).
 * Es idempotente: si ya existe un usuario con ese email o username no hace nada.
 * Nadie puede registrarse como ADMIN por la API; los demás admins los nombra este.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(AdminProperties properties, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.password() == null || properties.password().isBlank()) {
            log.info("ADMIN_PASSWORD no configurado: no se crea la cuenta de administrador inicial");
            return;
        }
        User admin = new User(properties.username(), properties.email(), passwordEncoder.encode(properties.password()));
        if (userRepository.existsByEmail(admin.getEmail()) || userRepository.existsByUsernameIgnoreCase(admin.getUsername())) {
            return;
        }
        admin.addRole(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow());
        admin.addRole(roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow());
        userRepository.save(admin);
        log.info("Cuenta de administrador inicial creada: {}", admin.getUsername());
    }
}
