//TODO: implement authentication right after registration

package org.me.cloudfilestorage.security.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.security.dtos.UserRequest;
import org.me.cloudfilestorage.security.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;



    public ResponseEntity<?> authenticate(UserRequest userRequest,  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
                   authentication = authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(userRequest.username(), userRequest.password()));


            } catch (AuthenticationException exception) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(securityContext,request, response);


        User user = new User();
        user.setUsername(authentication.getName());
        return ResponseEntity.ok(user);
    }

    public ResponseEntity<?> createNewUser(UserRequest request) {
        if (userService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exist");
        }
        User user = userService.createUser(request);
        return ResponseEntity.ok(user.getUsername());
    }
}
