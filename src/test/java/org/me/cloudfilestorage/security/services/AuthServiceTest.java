package org.me.cloudfilestorage.security.services;

import org.junit.jupiter.api.Test;
import org.me.cloudfilestorage.security.dtos.UserRequest;
import org.me.cloudfilestorage.security.entities.User;
import org.me.cloudfilestorage.security.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AuthServiceTest {

    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;

    @Container
    public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:latest")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("1234");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }


    @Test
    void testPostgres() {
        assertThat(postgres.isRunning()).isTrue();
    }


    @Test
    void testCreateUser_ShouldSaveInDataBase() {
        UserRequest request = new UserRequest("user","1234");

        userService.createUser(request);
        var user = userRepository.findByUsername("user");
        assertThat(user.isPresent());
        assertThat(user.get().getUsername()).isEqualTo(request.username());
    }

    @Test
    void testCreateUserWithNotUniqueUsername_ShouldReturnError() {
        User user1 = new User();
        user1.setUsername("unique_bob");
        user1.setPassword("pass");
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("unique_bob");
        user2.setPassword("other_pass");

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.save(user2);
        });
    }


}