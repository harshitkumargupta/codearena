package com.codearena.api.auth;

import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(AdminProperties properties, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (!properties.isComplete()) {
            log.warn("No ADMIN user exists and ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD are not all set; "
                    + "skipping admin bootstrap");
            return;
        }
        String email = properties.email().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(properties.username()) || userRepository.existsByEmail(email)) {
            log.warn("Cannot bootstrap ADMIN user: username or email is already used by a non-admin account");
            return;
        }
        userRepository.save(new User(properties.username(), email,
                passwordEncoder.encode(properties.password()), Role.ADMIN, clock.instant()));
        log.info("Bootstrapped ADMIN user '{}'", properties.username());
    }
}
