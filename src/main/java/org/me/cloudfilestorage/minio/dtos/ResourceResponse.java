package org.me.cloudfilestorage.minio.dtos;


import org.me.cloudfilestorage.minio.enums.ResourceType;

public record ResourceResponse(
        String path,
        String name,
        long size,
        ResourceType type
) {
}
