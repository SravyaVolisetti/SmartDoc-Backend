package com.smartdoc.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<Map<String, String>> handleResourceNotFound(
        ResourceNotFoundException exception) {

    exception.printStackTrace();

    Map<String, String> error = Map.of(
            "error", exception.getMessage()
    );

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}

@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, String>> handleGeneralException(
        Exception exception) {

    exception.printStackTrace();

    Map<String, String> error = Map.of(
            "error", "An unexpected error occurred"
    );

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
}


}
