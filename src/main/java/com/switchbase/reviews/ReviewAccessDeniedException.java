package com.switchbase.reviews;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(String message) {
        super(message);
    }
}
