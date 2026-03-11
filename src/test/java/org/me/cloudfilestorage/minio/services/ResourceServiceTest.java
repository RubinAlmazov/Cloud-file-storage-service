//TODO: add E2E test
package org.me.cloudfilestorage.minio.services;

import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Autowired
    MinioClient minioClient;

    @BeforeEach
    void createBucket() throws Exception{
        minioClient.makeBucket(MakeBucketArgs.builder().bucket("files-users").build());
    }

    @Test
    void testMinio() {
        assertThat(minio.isRunning()).isTrue();
    }

    @Test
    void TestCreateFolderForUser_ShouldReturnFolder() throws Exception{
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
}