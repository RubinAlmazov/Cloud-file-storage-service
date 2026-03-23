//TODO: add E2E test, figure out how ByteOutputStream works
package org.me.cloudfilestorage.minio.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.cloudfilestorage.minio.dtos.ResourceResponse;
import org.me.cloudfilestorage.minio.enums.ResourceType;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    void testFindResource_ShouldReturnFile() throws Exception{
        String bucketName = "files-users";
        String folderName = "files-" + 2 + "-users/info.txt";
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
        assertThat(responseResource.type()).isEqualTo(ResourceType.FILE);
    }

    @Test
    void testFindResource_ShouldReturnDirectory() throws Exception{
        String bucketName = "files-users";
        String folderName = "files-" + 2.1 + "-users/";
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
        assertThat(responseResource.type()).isEqualTo(ResourceType.DIRECTORY);
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
    void testDownloadResource_ShouldReturnFile() throws Exception {
        String bucketName = "files-users";
        String fileName = "files-" + 4 + "-users/info.txt";
        byte[] expect = "its a test".getBytes();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(new ByteArrayInputStream(expect), expect.length, -1)
                        .build()
        );

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ResponseEntity<?> responseEntity = resourceService.downloadResource(fileName, byteArrayOutputStream);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        byte[] content = byteArrayOutputStream.toByteArray();
        assertThat(content).isNotEmpty();
        assertThat(content).isEqualTo(expect);
    }

    @Test
    void testDownloadResource_ShouldReturnDirectory() throws Exception {
        String bucketName = "files-users";
        String folderName = "files-" + 4.1 + "-users/";
        String content = "its an info";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName + "user1_info")
                        .stream(new ByteArrayInputStream((content + " user1").getBytes()), (content + " user1").getBytes().length, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderName + "user2_info")
                        .stream(new ByteArrayInputStream((content + " user2").getBytes()) , (content + " user2").getBytes().length, -1)
                        .build()
        );

        assertThat(minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(folderName).build())).hasSize(2);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ResponseEntity<?> responseEntity = resourceService.downloadResource(folderName, byteArrayOutputStream);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> objectNames = new ArrayList<>();
        Map<String, String> objectsContent = new HashMap<>();

        byte[] bytes = byteArrayOutputStream.toByteArray();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                objectNames.add(zipEntry.getName());

                String contents = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                objectsContent.put(zipEntry.getName(), contents);
            }
        }

        assertThat(objectsContent.get("user1_info")).contains("its an info user1");
        assertThat(objectsContent.get("user2_info")).contains("its an info user2");
    }

    @Test
    void testRenameFile_ShouldReturnOk() throws Exception{
        String bucketName = "files-users";
        String path = "files-" + 5 + "-users";
        String content = "its an info";
        String path2 = "files-" + 5.1 + "-users";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .stream(new ByteArrayInputStream((content + " user5").getBytes()), (content + " user5").getBytes().length, -1)
                        .build()
        );

        ResponseEntity<?> responseEntity = resourceService.renameResource(path,path2);
        String fileContent;
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(path2).build())) {

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            inputStream.transferTo(byteArrayOutputStream);
            byte[] content2 = byteArrayOutputStream.toByteArray();
            fileContent = new String(content2, StandardCharsets.UTF_8);

        }
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fileContent).isEqualTo(content + " user5");
        assertThrows(ErrorResponseException.class, () -> minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(path).build()));
    }

    @Test
    void testRenameDirectory_ShouldReturnOk() throws Exception{
        String bucketName = "files-users";
        String path = "files-" + 6 + "-users/";
        String path2 = "files-" + 6.1 + "-users/";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path + "user_info_1")
                        .stream(new ByteArrayInputStream(("its an user 6 info").getBytes()), ("its an user 6 info").getBytes().length, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path + "user_info_2")
                        .stream(new ByteArrayInputStream(("its an user 7 info").getBytes()), ("its an user 7 info").getBytes().length, -1)
                        .build()
        );

        ResponseEntity<?> responseEntity = resourceService.renameResource(path,path2);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThrows(ErrorResponseException.class, () -> minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(path).build()));
        assertThat(responseEntity.getBody()).isEqualTo(ResourceType.DIRECTORY);

        Map<String, String> objectsContent = new HashMap<>();
        Iterable<Result<Item>> resultIterable = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(path2).recursive(true).build());
        for (Result<Item> result : resultIterable) {
                try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(result.get().objectName()).build())) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    inputStream.transferTo(byteArrayOutputStream);
                    byte[] fileContent = byteArrayOutputStream.toByteArray();
                    String str = new String(fileContent,StandardCharsets.UTF_8);
                    objectsContent.put(result.get().objectName(), str);
                }
            }
        System.out.println(objectsContent.keySet());

        assertThat(objectsContent.get(path2 + "user_info_1")).isEqualTo("its an user 6 info");
        assertThat(objectsContent.get(path2 + "user_info_2")).isEqualTo("its an user 7 info");
        assertThrows(ErrorResponseException.class, () -> minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(path).build()));
    }

}