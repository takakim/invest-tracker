package com.takakim.investtracker.api;

import com.takakim.investtracker.service.ConflictException;
import com.takakim.investtracker.service.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("ApiExceptionHandler converts exceptions into RFC 9457 ProblemDetails")
    void handleExceptions() throws NoSuchMethodException {
        ProblemDetail pd404 = handler.notFound(new ResourceNotFoundException("Not found"));
        assertEquals(HttpStatus.NOT_FOUND.value(), pd404.getStatus());
        assertEquals("Not found", pd404.getDetail());

        ProblemDetail pd409 = handler.conflict(new ConflictException("Conflict"));
        assertEquals(HttpStatus.CONFLICT.value(), pd409.getStatus());
        assertEquals("Conflict", pd409.getDetail());

        ProblemDetail pd400 = handler.badRequest(new IllegalArgumentException("Bad arg"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), pd400.getStatus());
        assertEquals("Bad arg", pd400.getDetail());

        // IllegalStateException also handled by badRequest
        ProblemDetail pd400state = handler.badRequest(new IllegalStateException("Bad state"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), pd400state.getStatus());
        assertEquals("Bad state", pd400state.getDetail());

        // null message falls back to title
        ProblemDetail pdNull = handler.notFound(new ResourceNotFoundException(null));
        assertEquals(HttpStatus.NOT_FOUND.value(), pdNull.getStatus());
        assertEquals("Resource not found", pdNull.getDetail());

        // MethodArgumentNotValidException
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
        bindingResult.addError(new FieldError("testObj", "name", "must not be blank"));
        MethodParameter methodParameter = new MethodParameter(
                ApiExceptionHandlerTests.class.getDeclaredMethod("handleExceptions"), -1);
        MethodArgumentNotValidException validEx = new MethodArgumentNotValidException(methodParameter, bindingResult);
        ProblemDetail pdValidation = handler.validation(validEx);
        assertEquals(HttpStatus.BAD_REQUEST.value(), pdValidation.getStatus());
        assertNotNull(pdValidation.getProperties());
    }
}
