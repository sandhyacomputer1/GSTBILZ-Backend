package com.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.entity.UserEntity;
import com.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeAdmin("rupnar8459@gmail.com", "Super Admin 1", "admin123");
        initializeAdmin("sandhyacomputer1@gmail.com", "Super Admin 2", "admin123");
    }

    private void initializeAdmin(String email, String name, String rawPassword) {
        if (userRepository.findByEmail(email).isPresent()) {
            logger.info("Super Admin with email {} already exists.", email);
            return;
        }

        long superAdminCount = userRepository.countByRole("ROLE_SUPERADMIN");
        if (superAdminCount >= 2) {
            logger.warn("Cannot create more Super Admins. Maximum limit of 2 reached (current count: {}).",
                    superAdminCount);
            return;
        }

        UserEntity superAdmin = UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .email(email)
                .name(name)
                .password(passwordEncoder.encode(rawPassword))
                .role("ROLE_SUPERADMIN")
                .isVerified(true)
                .accountStatus("APPROVED")
                .loginProvider("EMAIL")
                .build();

        userRepository.save(superAdmin);
        logger.info("Successfully created Super Admin: {} ({})", name, email);
    }
}
