
package org.me.cloudfilestorage.minio.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.me.cloudfilestorage.minio.dtos.ResourceResponse;
import org.me.cloudfilestorage.minio.enums.ResourceType;
import org.me.mytinyparser.dto.Parts;
import org.me.mytinyparser.MyTinyParserApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
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
        ResourceResponse resourceResponse;
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

                resourceResponse = new ResourceResponse(path, path2, byteArrayOutputStream.size(), resourceType);
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

            resourceResponse = new ResourceResponse(path, path2, 0, resourceType);
        }

        return ResponseEntity.ok(resourceResponse);
    }


    public ResponseEntity<?> findResourceByQuery(String query) {
        if (query.matches(".*[0-9/?'].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid characters used");
        }

        List<ResourceResponse> matches = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build());

            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                ResourceType resourceType = result.get().isDir() ? ResourceType.DIRECTORY : ResourceType.FILE;
                long size = resourceType.equals(ResourceType.FILE) ? result.get().size() : 0;

                Iterable<Result<Item>> fileNames = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(objectName).build());
                for (Result<Item> fileName : fileNames) {
                    if (query.isEmpty()) {
                        matches.add(new ResourceResponse(
                                objectName,
                                fileName.get().objectName().substring(objectName.lastIndexOf("/")+1),
                                size, resourceType));
                    }
                    else  {
                        if ((objectName + fileName.get().objectName()).contains(query) ) {
                            matches.add(new ResourceResponse(
                                    objectName,
                                    fileName.get().objectName().substring(objectName.lastIndexOf("/")+1),
                                     size, resourceType));
                        }
                    }

                }
            }

            if (matches.isEmpty()) {
                return ResponseEntity.ok(new int[] {});
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while accessing the file storage.");
        }

        return ResponseEntity.ok(matches);
    }

    public ResponseEntity<?> uploadResource(String path, HttpServletRequest request) {
        List<ResourceResponse> resourceResponses = new ArrayList<>();
        try {
            MyTinyParserApplication tinyParserApplication = new MyTinyParserApplication();
            List<Parts> parts = tinyParserApplication.parseAll(request);

            for (Parts part : parts) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName).object(path + part.contentDisposition().getFileName()).stream(part.resourceContent(),
                                part.size(),-1 ).build());

                ResourceResponse resourceResponse = new ResourceResponse(path,
                        part.contentDisposition().getFileName(), part.size(), ResourceType.FILE);
                resourceResponses.add(resourceResponse);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok(resourceResponses);
    }

    public ResponseEntity<?> getListOfFolders() {
        List<String> listOfFolders = new ArrayList<>();
        try {
            Iterable<Result<Item>> listOfObjects = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build());
            for (Result<Item> item : listOfObjects) {
                listOfFolders.add(item.get().objectName());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return !listOfFolders.isEmpty() ? ResponseEntity.ok(listOfFolders) : ResponseEntity.ok("No folders");
    }

    public ResponseEntity<?> getDirectoryInfo(String path) {
        List<ResourceResponse> resourceResponses = new ArrayList<>();
        try {
            Iterable<Result<Item>> listOfObjects = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(path).build());
            for (Result<Item> item : listOfObjects) {
                String folderPath = item.get().objectName();
                resourceResponses.add(new ResourceResponse(
                        folderPath,
                        folderPath.substring(folderPath.lastIndexOf("/")+1),
                        item.get().size(), ResourceType.FILE));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return ResponseEntity.ok(resourceResponses);
    }


    public ResponseEntity<?> createEmptyFolder(String path) throws Exception{
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .stream(new ByteArrayInputStream(new byte[] {}), 0, -1)
                            .build()
            );
        } catch (ErrorResponseException e) {
            throw new RuntimeException("Folder already exists");
        }
        return ResponseEntity.ok(HttpStatus.CREATED);
    }
}
