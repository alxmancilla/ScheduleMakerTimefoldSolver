package com.example.web.security;

import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first ADMIN user on startup when the app_user table is empty, using
 * the ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD environment variables.
 * Does nothing if users already exist or no bootstrap password is configured.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrap(AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap-username:admin}") String bootstrapUsername,
            @Value("${app.admin.bootstrap-password:}") String bootstrapPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("No application users exist and ADMIN_BOOTSTRAP_PASSWORD is not set; "
                    + "skipping admin bootstrap. Set ADMIN_BOOTSTRAP_PASSWORD to seed the first ADMIN user.");
            return;
        }
        AppUserEntity admin = new AppUserEntity(
                bootstrapUsername,
                passwordEncoder.encode(bootstrapPassword),
                "ADMIN",
                true);
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN user '{}' (app_user table was empty).", bootstrapUsername);
    }
}
