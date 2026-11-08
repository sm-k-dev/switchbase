package com.switchbase.ratings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RatingAccessDeniedException extends RuntimeException {

    public RatingAccessDeniedException(String message) {
        super(message);
    }
}
