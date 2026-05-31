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
    private final String region;

    public S3Util(S3Client s3Client,
                  @Value("${app.aws.s3.bucket}") String bucket,
                  @Value("${app.aws.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    public String uploadImage(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build(),
            RequestBody.fromBytes(bytes)
        );
        log.debug("Uploaded image S3 object: {}", key);
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    public String extractKey(String url) {
        String prefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        return url != null && url.startsWith(prefix) ? url.substring(prefix.length()) : null;
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
