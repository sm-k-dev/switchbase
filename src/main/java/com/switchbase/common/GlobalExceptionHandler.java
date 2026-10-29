package com.switchbase.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ModelAndView notFound() {
        ModelAndView mav = new ModelAndView("error", HttpStatus.NOT_FOUND);
        mav.addObject("status", 404);
        return mav;
    }
}
