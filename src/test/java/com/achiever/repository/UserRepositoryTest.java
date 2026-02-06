package com.achiever.repository;

import com.achiever.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should find user by email")
    void shouldFindByEmail() {
        // Given
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should find user by exact email")
    void shouldFindByExactEmail() {
        // Given
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should check if username exists")
    void shouldCheckIfUsernameExists() {
        // Given
        User user = User.builder()
                .username("uniqueuser")
                .email("unique@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        // When/Then
        assertThat(userRepository.existsByUsername("uniqueuser")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Should not find user by non-existent email")
    void shouldNotFindByNonExistentEmail() {
        // When
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should save user with all fields")
    void shouldSaveUserWithAllFields() {
        // Given
        User user = User.builder()
                .username("newuser")
                .email("new@example.com")
                .passwordHash("hashedpassword")
                .timezone("America/New_York")
                .build();

        // When
        User saved = userRepository.save(user);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashedpassword");
        assertThat(saved.getTimezone()).isEqualTo("America/New_York");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .email("duplicate@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(user1);
        entityManager.flush();

        User user2 = User.builder()
                .username("user2")
                .email("duplicate@example.com")
                .timezone("UTC")
                .build();

        // When/Then
        assertThatThrownBy(() -> {
            entityManager.persist(user2);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should enforce unique username constraint")
    void shouldEnforceUniqueUsernameConstraint() {
        // Given
        User user1 = User.builder()
                .username("sameusername")
                .email("user1@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(user1);
        entityManager.flush();

        User user2 = User.builder()
                .username("sameusername")
                .email("user2@example.com")
                .timezone("UTC")
                .build();

        // When/Then
        assertThatThrownBy(() -> {
            entityManager.persist(user2);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
