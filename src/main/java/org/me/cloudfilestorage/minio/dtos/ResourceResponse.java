package org.me.cloudfilestorage.minio.dtos;


public record ResourceResponse(
        String path,
        String name,
        long size,
        String type
) {
}
