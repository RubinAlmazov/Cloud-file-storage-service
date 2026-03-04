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
    public ResponseEntity<?> registration() {
        return authService.createUser();
    }

    @GetMapping("/log-out")
    public ResponseEntity<?> logout() {
        return authService.logout();
    }


}
