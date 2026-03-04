package org.me.cloudfilestorage.security.services;

import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.security.dtos.UserRequest;
import org.me.cloudfilestorage.security.entities.User;
import org.me.cloudfilestorage.security.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
//    private final PasswordEncoder passwordEncoder;



    public ResponseEntity<?> authenticate(UserRequest request) {
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(request.username(), request.password())));
        }
        catch (AuthenticationException exception) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        return ResponseEntity.ok(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public ResponseEntity<?> createUser(UserRequest request) {
        try {
            User user = new User();
            user.setUsername(request.username());
//            user.setPassword(passwordEncoder.encode(request.password()));
            user.setPassword("{noop}" + request.password());
            userRepository.save(user);

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exist");
        }

        return ResponseEntity.ok(request.username());
    }

    public ResponseEntity<?> logout() {
        return ResponseEntity.ok("ok");
    }
}
