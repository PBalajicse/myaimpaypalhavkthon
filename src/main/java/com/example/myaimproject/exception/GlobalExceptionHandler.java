package com.example.myaimproject.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllExceptions(Exception ex) {
        return new ResponseEntity<>("ப்ராசஸ் செய்வதில் ஒரு சிறிய தொழில்நுட்ப சிக்கல்: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
