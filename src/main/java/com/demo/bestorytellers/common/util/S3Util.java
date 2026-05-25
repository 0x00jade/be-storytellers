package com.demo.bestorytellers.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

@Component
public class S3Util {

    private static final Logger log = LoggerFactory.getLogger(S3Util.class);

    private final S3Client s3Client;
    private final String bucket;

    public S3Util(S3Client s3Client, @Value("${app.aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public void uploadContent(String key, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .contentLength((long) bytes.length)
                .build(),
            RequestBody.fromBytes(bytes)
        );
        log.debug("Uploaded S3 object: {}", key);
    }

    public String fetchContent(String key) {
        var response = s3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );
        return response.asUtf8String();
    }

    public void deleteObject(String key) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );
        log.debug("Deleted S3 object: {}", key);
    }
}
