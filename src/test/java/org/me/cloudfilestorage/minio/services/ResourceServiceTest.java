//TODO: add E2E test
package org.me.cloudfilestorage.minio.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.cloudfilestorage.minio.dtos.ResourceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class ResourceServiceTest {


    @Container
    public static MinIOContainer minio = new MinIOContainer("minio/minio:latest")
            .withUserName("user")
            .withPassword("123456789");

    @DynamicPropertySource
    static void registerMinioProps(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        registry.add("minio.endpoint", () -> endpoint);
        registry.add("minio.access-key", () -> "user");
        registry.add("minio.secret-key", () -> "123456789");
        registry.add("minio.bucket-name", () -> "files-users");
    }

    @Autowired
    MinioClient minioClient;

    @Autowired
    ResourceService resourceService;

    @BeforeEach
    void createBucket() throws Exception{
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket("files-users").build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("files-users").build());
        }
    }

    @Test
    void testMinio() {
        assertThat(minio.isRunning()).isTrue();
    }

    @Test
    void testCreateFolderForUser_ShouldReturnFolder() throws Exception{
        String bucketName = "files-users";
        String folderName = "files-" + 1 + "-users/";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName)
                        .stream(new ByteArrayInputStream(new byte[] {}), 0, -1)
                        .build()
        );

        assertThat(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())).isTrue();
        assertThat(minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(folderName).build()))
                .returns(bucketName, StatObjectResponse::bucket)
                .returns(folderName, StatObjectResponse::object);

    }

    @Test
    void testFindResource_ShouldReturnResource() throws Exception{
        String bucketName = "files-users";
        String folderName = "files-" + 2 + "-users/";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName)
                        .stream(new ByteArrayInputStream(new byte[] {}), 0, -1)
                        .build()
        );

        ResponseEntity<?> responseEntity = resourceService.findResource(folderName);
        assertThat(responseEntity.getBody()).isInstanceOf(ResourceResponse.class);
        ResourceResponse responseResource = (ResourceResponse) responseEntity.getBody();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseResource.name()).isEqualTo(bucketName);
        assertThat(responseResource.size()).isEqualTo(0);
        assertThat(responseResource.type()).isEqualTo("application/octet-stream");
    }

    @Test
    void testDeleteResource_ShouldReturnOk() throws Exception{
        String bucketName = "files-users";
        String folderName = "files-" + 3 + "-users/";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName)
                        .stream(new ByteArrayInputStream("to delete".getBytes()), 9, -1)
                        .build()
        );

        ResponseEntity<?> responseEntity = resourceService.deleteResource(folderName);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThrows(ErrorResponseException.class, () -> minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(folderName).build()));

    }

    @Test
    void testDownloadResource_ShouldReturnResource() throws Exception {
        String bucketName = "files-users";
        String folderName = "files-" + 4 + "-users/";
        byte[] expect = "its a test".getBytes();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName)
                        .stream(new ByteArrayInputStream(expect), expect.length, -1)
                        .build()
        );

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ResponseEntity<?> responseEntity = resourceService.downloadResource(folderName, byteArrayOutputStream);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        byte[] content  = byteArrayOutputStream.toByteArray();
        assertThat(content).isNotEmpty();
        assertThat(content).isEqualTo(expect);

    }
}