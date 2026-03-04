package org.me.cloudfilestorage.security.controllers;


import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.security.dtos.UserRequest;
import org.me.cloudfilestorage.security.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/sign-in")
    public ResponseEntity<?> authorization(@RequestBody UserRequest userRequest) {
        return authService.authenticate(userRequest);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<?> registration(@RequestBody UserRequest userRequest) {
        return authService.createUser(userRequest);
    }

    @GetMapping("/log-out")
    public ResponseEntity<?> logout() {
        return authService.logout();
    }


}
