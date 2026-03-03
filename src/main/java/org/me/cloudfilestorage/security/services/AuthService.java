package org.me.cloudfilestorage.security.services;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public ResponseEntity<?> createUser() {
        return ResponseEntity.ok("ok");
    }

    public ResponseEntity<?> logout() {
        return ResponseEntity.ok("ok");
    }

    public ResponseEntity<?> authorization() {
        return ResponseEntity.ok("ok");
    }
}
