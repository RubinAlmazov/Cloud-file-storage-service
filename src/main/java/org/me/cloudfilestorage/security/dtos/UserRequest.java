package org.me.cloudfilestorage.security.dtos;

public record UserRequest(
        String username,
        String password
) {
}
