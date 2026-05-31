package com.einvoice.api.admin.service;

import com.einvoice.api.admin.dto.UserCreateRequest;
import com.einvoice.api.admin.dto.UserResponse;
import com.einvoice.api.admin.dto.UserUpdateRequest;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.error.EmailAlreadyExistsException;
import com.einvoice.core.error.LastSuperUserProtectedException;
import com.einvoice.core.repository.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for user administration operations. */
@Service
@Transactional
public class AdminUserService {

    private static final long SUPER_USER_INVARIANT_KEY = 99999L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    /**
     * Constructs an AdminUserService with the required dependencies.
     *
     * @param userRepository the user repository
     * @param passwordEncoder the password encoder
     * @param entityManager the entity manager
     */
    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /**
     * Creates a new user.
     *
     * @param request the user creation request
     * @return the created user response
     */
    public UserResponse create(UserCreateRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(u -> {
            throw new EmailAlreadyExistsException("Email '" + request.email() + "' already exists");
        });

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .isSuperUser(request.isSuperUser() != null ? request.isSuperUser() : false)
                .isActive(true)
                .build();
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    /**
     * Updates an existing user.
     *
     * @param id the user ID
     * @param request the user update request
     * @return the updated user response
     */
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            userRepository.findByEmail(request.email()).ifPresent(u -> {
                throw new EmailAlreadyExistsException("Email '" + request.email() + "' already exists");
            });
            user.setEmail(request.email());
        }

        if (request.name() != null) {
            user.setName(request.name());
        }

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.isSuperUser() != null) {
            boolean wasSuperUser = Boolean.TRUE.equals(user.getIsSuperUser());
            boolean willBeSuperUser = request.isSuperUser();
            if (wasSuperUser && !willBeSuperUser) {
                enforceSuperUserInvariant(id);
            }
            user.setIsSuperUser(willBeSuperUser);
        }

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    /**
     * Activates a user by setting isActive to true.
     *
     * @param id the user ID
     */
    public void activate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsActive(true);
        userRepository.save(user);
    }

    /**
     * Deactivates a user by setting isActive to false.
     *
     * @param id the user ID
     */
    public void deactivate(UUID id) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getIsSuperUser())) {
            enforceSuperUserInvariant(id);
        }

        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Lists users, optionally including inactive ones.
     *
     * @param includeInactive whether to include inactive users
     * @return the list of user responses
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list(boolean includeInactive) {
        List<User> users = includeInactive
                ? userRepository.findAll()
                : userRepository.findAll().stream()
                        .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                        .toList();
        return users.stream().map(this::toResponse).toList();
    }

    private void enforceSuperUserInvariant(UUID targetId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", SUPER_USER_INVARIANT_KEY)
                .getSingleResult();

        long otherActiveSuperUsers = userRepository
                .countByIsSuperUserTrueAndIsActiveTrueAndIdNot(targetId);
        if (otherActiveSuperUsers == 0) {
            throw new LastSuperUserProtectedException(
                    "Cannot perform this action: this is the last active Super User");
        }
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getName(), u.getEmail(), u.getIsSuperUser(),
                u.getIsActive(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
