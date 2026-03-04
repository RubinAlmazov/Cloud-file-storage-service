//TODO: implement authentication right after registration

package org.me.cloudfilestorage.security.services;

import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.security.dtos.UserRequest;
import org.me.cloudfilestorage.security.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;



    public ResponseEntity<?> authenticate(UserRequest request) {
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(request.username(), request.password())));
            } catch (AuthenticationException exception) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

        return ResponseEntity.ok(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public ResponseEntity<?> createNewUser(UserRequest request) {
        if (userService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exist");
        }
        User user = userService.createUser(request);
        return ResponseEntity.ok(user.getUsername());
    }

    public ResponseEntity<?> logout() {
        return ResponseEntity.ok("ok");
    }
}
