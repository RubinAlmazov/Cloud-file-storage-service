
package org.me.cloudfilestorage.minio.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import org.me.cloudfilestorage.minio.dtos.ResourceResponse;
import org.me.cloudfilestorage.minio.enums.ResourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.xmlunit.builder.Input;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class ResourceService {

    private final String bucketName;

    private final MinioClient minioClient;

    public ResourceService(
            @Value("${minio.bucket-name:user-files}") String bucketName,
            MinioClient minioClient
    ) {
        this.bucketName = bucketName;
        this.minioClient = minioClient;
    }

    public void createFolderForUser(String userId) {
        String folderName = "user-" + userId + "-files/";

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(folderName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                try {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(folderName)
                                    .stream(new ByteArrayInputStream(new byte[] {}), 0, -1)
                                    .build()
                    );
                } catch (Exception ex) {
                    throw new RuntimeException("Ошибка при создании папки", ex);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при проверке папки", e);
        }
    }

    public ResponseEntity<?> findResource(String path) throws Exception {
        StatObjectResponse stats;
        try {
            stats = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (ErrorResponseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No such path");
        }

        ResourceType type = path.endsWith("/") ? ResourceType.DIRECTORY : ResourceType.FILE;

        ResourceResponse response = new ResourceResponse(
                path,
                stats.bucket(),
                stats.size(),
                type
        );

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?>deleteResource(String path) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (ErrorResponseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No such path");
        }
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build()
        );
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    public ResponseEntity<?> downloadResource(String path, OutputStream outputStream) throws Exception{
        ResourceType type = path.endsWith("/") ? ResourceType.DIRECTORY : ResourceType.FILE;
        if (type.equals(ResourceType.FILE)) {
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build()) )
            {
                byte[] buffer = new byte[8192];
                int byteRead;
                while ( (byteRead = stream.read(buffer)) != -1) {

                    outputStream.write(buffer, 0, byteRead);
                }


            } catch (ErrorResponseException e) {
                throw e;
            }

        }
        else {
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(path).recursive(true).build());
            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                if (objectName.endsWith("/")) {
                    continue;
                }
                try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()) )
                {
                    ZipEntry zipEntry = new ZipEntry(objectName.substring(path.length()));
                    zipOutputStream.putNextEntry(zipEntry);
                    stream.transferTo(zipOutputStream);
                    zipOutputStream.closeEntry();
                } catch (ErrorResponseException e) {
                    throw e;
                }
            }
            zipOutputStream.close();
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    //TODO:find out is it necessary with second try block in rename method
    public ResponseEntity<?> renameResource(String path, String path2) throws Exception{
        String pathDirectory = path.substring(0, path.lastIndexOf("/") + 1);
//        if (!path2.substring(0, path.lastIndexOf("/")+1).equals(pathDirectory)) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Paths to directory doesnt match");
//        }

        ResourceType resourceType = path.endsWith("/") ? ResourceType.DIRECTORY : ResourceType.FILE;
        if (resourceType.equals(ResourceType.FILE)) {
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(path).build())) {
                Iterable<Result<Item>> listOfObjects = minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(bucketName).prefix(pathDirectory).build());
                for (Result<Item> result : listOfObjects) {
                    if (result.get().objectName().equals(path2)) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File already exist");
                    }
                }

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                inputStream.transferTo(byteArrayOutputStream);
                byte[] fileContent = byteArrayOutputStream.toByteArray();

                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(path).build());

                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName).object(path2).stream(new ByteArrayInputStream(fileContent), fileContent.length, -1)
                        .build());
            }
            catch (ErrorResponseException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No such path");
            }

        }
        else {
            //TODO: add validation for directory, find out is it necessary to add try block

            Iterable<Result<Item>> listOfObjects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName).prefix(path).recursive(true).build());


            for (Result<Item> result : listOfObjects) {
                String objectName = result.get().objectName();
                try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    inputStream.transferTo(byteArrayOutputStream);
                    byte[] fileContent = byteArrayOutputStream.toByteArray();

                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName).object(path2 + objectName.substring(objectName.lastIndexOf("/"))).stream(new ByteArrayInputStream(fileContent), fileContent.length, -1)
                            .build());
                }
            }
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(path).build());
        }

        return ResponseEntity.ok(ResourceType.DIRECTORY);

    }
}
