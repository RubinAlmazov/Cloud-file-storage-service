package org.me.cloudfilestorage.security.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.me.cloudfilestorage.security.entities.User;
import org.me.cloudfilestorage.security.repositories.UserRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;



    @Test
    void findByUsername() {
        User user = mock(User.class);
        when(userRepository.findByUsername("user_test")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByUsername("user_test");

        assertTrue(result.isPresent(), "Ожидается, что Optional будет присутствовать");
        assertSame(user, result.get(), "Должен вернуться тот же объект User, что вернул репозиторий");
        verify(userRepository).findByUsername("user_test");
    }

}