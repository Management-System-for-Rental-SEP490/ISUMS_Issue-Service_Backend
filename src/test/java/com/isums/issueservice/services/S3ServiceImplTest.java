package com.isums.issueservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3ServiceImpl (issue-service)")
class S3ServiceImplTest {

    @Mock private S3Client s3Client;

    @InjectMocks private S3ServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "cloudFrontDomain", "cdn.example.com");
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("uses folder parameter in key (fixed: was literal 'folder')")
        void folderUsed() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.PNG", "image/png",
                    "data".getBytes(StandardCharsets.UTF_8));

            String key = service.upload(file, "issue/42");

            assertThat(key).startsWith("media/issue/42/");
            assertThat(key).endsWith(".png");
            ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(cap.capture(), any(RequestBody.class));
            assertThat(cap.getValue().bucket()).isEqualTo("test-bucket");
        }

        @Test
        @DisplayName("defaults extension to jpg when filename has none")
        void noExt() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "noext", "image/jpeg", new byte[]{1});

            assertThat(service.upload(file, "f")).endsWith(".jpg");
        }

        @Test
        @DisplayName("wraps IOException into RuntimeException")
        void ioException() {
            assertThatThrownBy(() -> service.upload(new BrokenMultipartFile(), "f"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes with bucket + key")
        void deletes() {
            service.delete("media/issue/42/x.png");

            ArgumentCaptor<DeleteObjectRequest> cap = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(cap.capture());
            assertThat(cap.getValue().key()).isEqualTo("media/issue/42/x.png");
        }

        @Test
        @DisplayName("swallows S3 errors")
        void swallows() {
            doThrow(AwsServiceException.builder().message("boom").build())
                    .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

            service.delete("k");
        }
    }

    @Test
    @DisplayName("getImageUrl builds CDN URL")
    void cdnUrl() {
        assertThat(service.getImageUrl("media/x.jpg"))
                .isEqualTo("https://cdn.example.com/media/x.jpg");
    }

    private static class BrokenMultipartFile implements MultipartFile {
        @Override public String getName() { return "broken"; }
        @Override public String getOriginalFilename() { return "x.jpg"; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return false; }
        @Override public long getSize() { return 1L; }
        @Override public byte[] getBytes() { return new byte[]{1}; }
        @Override public InputStream getInputStream() throws IOException {
            throw new IOException("simulated");
        }
        @Override public void transferTo(java.io.File dest) {}
    }
}
