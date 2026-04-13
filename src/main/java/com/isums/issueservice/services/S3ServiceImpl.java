package com.isums.issueservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl {

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.cloudfront-domain}")
    private String cloudFrontDomain;

    public String upload(MultipartFile file, String folder) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String key = "media/" + folder + "/" + UUID.randomUUID() + "." + ext;

            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 uploaded key={}", key);
            return key;
        } catch (IOException e) {
            log.error("S3 upload failed", e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("S3 deleted key={}", key);
        } catch (Exception e) {
            log.error("S3 delete failed key={}", key, e);
        }
    }

    public String getImageUrl(String key) {
        return "https://" + cloudFrontDomain + "/" + key;
    }

    private String getExtension(String fileName) {
        if(fileName == null || !fileName.contains(".")) return "jpg";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
