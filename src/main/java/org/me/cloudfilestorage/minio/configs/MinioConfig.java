package org.me.cloudfilestorage.minio.configs;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:user}")
    private String accessKey;

    @Value("${minio.secret-key:123456789}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }


    @Bean
    public CommandLineRunner ensureBucket(MinioClient minioClient) {
        return args -> {
            String bucketName = "user-files";
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucketName).build()
                );

                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    System.out.println("Created bucket: " + bucketName);
                } else {
                    System.out.println("Bucket already exists: " + bucketName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }
}
