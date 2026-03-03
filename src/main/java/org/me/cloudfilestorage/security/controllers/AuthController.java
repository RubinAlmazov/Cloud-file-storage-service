package org.me.cloudfilestorage.security.controllers;


import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.security.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/test")
    public String test() {
        return "It is works";
    }

    @GetMapping("/sign-in")
    public ResponseEntity<?> authorization() {
        return authService.authorization();
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
