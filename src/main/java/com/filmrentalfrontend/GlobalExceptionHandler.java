package com.filmrentalfrontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        LOGGER.error("Unhandled exception", e);
        model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
        return "error";
    }


    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNotFound(NoHandlerFoundException e, Model model) {
        LOGGER.error("Page not found: {}", e.getRequestURL());
        model.addAttribute("error", "Page not found: " + e.getRequestURL());
        return "error";
    }
}