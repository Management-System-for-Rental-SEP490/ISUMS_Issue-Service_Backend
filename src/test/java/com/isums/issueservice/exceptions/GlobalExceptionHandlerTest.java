package com.isums.issueservice.exceptions;

import com.isums.issueservice.domains.dtos.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler (issue-service)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDb returns 500 with DB_ERROR code and root cause message")
    void db() {
        DataAccessException ex = new DataAccessException("outer", new RuntimeException("root")) {};
        ResponseEntity<ApiResponse<Void>> res = handler.handleDb(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("DB_ERROR");
        assertThat(res.getBody().getErrors().get(0).getMessage()).isEqualTo("root");
    }

    @Test
    @DisplayName("handleBadRequest returns 400")
    void badRequest() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadRequest(new IllegalArgumentException("bad"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("handleGeneric returns 500")
    void generic() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneric(new Exception("boom"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("handleDataIntegrityViolation returns 409 with serial_number-specific message")
    void dataIntegritySerial() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key violates uk_serial_number");
        ResponseEntity<ApiResponse<?>> res = handler.handleDataIntegrityViolation(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getMessage()).isEqualTo("Serial number already exists");
    }

    @Test
    @DisplayName("handleDataIntegrityViolation returns 409 generic message otherwise")
    void dataIntegrityGeneric() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("fk violation");
        ResponseEntity<ApiResponse<?>> res = handler.handleDataIntegrityViolation(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getMessage()).isEqualTo("Data integrity violation");
    }

    @Test
    @DisplayName("handleConflictException returns 409")
    void conflict() {
        ResponseEntity<ApiResponse<?>> res =
                handler.handleConflictException(new ConflictException("dup"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getMessage()).isEqualTo("dup");
    }

    @Test
    @DisplayName("handleNotFound returns 404 with NOT_FOUND code")
    void notFound() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleNotFound(new NotFoundException("missing"));
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("NOT_FOUND");
    }
}
