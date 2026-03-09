package org.me.cloudfilestorage.security.controllers;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public ResponseEntity<?> authorization(@RequestBody UserRequest userRequest,  HttpServletRequest request, HttpServletResponse response) {
        return authService.authenticate(userRequest, request, response);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<?> registration(@RequestBody UserRequest userRequest) {
        return authService.createNewUser(userRequest);
    }


}
