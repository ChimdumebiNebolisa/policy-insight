package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartException;

class GlobalExceptionHandlerTests {

    @Test
    void multipartExceptionReturnsSafeHtmxError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("HX-Request", "true");

        Object response = handler.multipartUpload(new MultipartException("socket timeout secret detail"), request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody().toString())
                .contains("The upload could not be completed. Try a smaller PDF or upload again.")
                .doesNotContain("socket timeout")
                .doesNotContain("secret detail");
    }
}
