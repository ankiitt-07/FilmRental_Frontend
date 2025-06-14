package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.CityDTO;
import com.filmrentalfrontend.model.dto.PageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Controller
@RequestMapping("/cities")
public class CityController {

    private static final Logger logger = LoggerFactory.getLogger(CityController.class);
    private final RestTemplate restTemplate;

    @Value("${backend.api.url:http://localhost:8080/api/cities}")
    private String backendApiUrl;

    public CityController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String getAllCities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        // Validate page and size parameters
        if (page < 0 || size <= 0) {
            logger.warn("Invalid pagination parameters: page={}, size={}", page, size);
            model.addAttribute("error", "Invalid pagination parameters. Page must be non-negative, and size must be positive.");
            model.addAttribute("cities", new PageDTO<CityDTO>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 10);
            model.addAttribute("size", 10);
            return "city";
        }

        try {
            String url = backendApiUrl + "?page=" + page + "&size=" + size; // Fixed to /api/cities
            logger.info("Fetching cities from: {}", url);

            ParameterizedTypeReference<PageDTO<CityDTO>> responseType =
                    new ParameterizedTypeReference<PageDTO<CityDTO>>() {};
            ResponseEntity<PageDTO<CityDTO>> response = restTemplate.exchange(url, HttpMethod.GET, null, responseType);

            logger.info("Response status: {}", response.getStatusCode());
            PageDTO<CityDTO> pageResponse = response.getBody();

            if (pageResponse == null) {
                logger.warn("Received null response body from: {}", url);
                model.addAttribute("error", "No data received from the API. Please check the backend service.");
                model.addAttribute("cities", new PageDTO<CityDTO>());
            } else {
                logger.info("Received {} cities for page: {}",
                        pageResponse.getContent() != null ? pageResponse.getContent().size() : 0, page);
                model.addAttribute("cities", pageResponse);
                model.addAttribute("currentPage", page);
                model.addAttribute("pageSize", size);
                model.addAttribute("size", size);
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching cities: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("error", "Failed to fetch cities: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            model.addAttribute("cities", new PageDTO<CityDTO>());
        } catch (RestClientException e) {
            logger.error("Error fetching cities: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to fetch cities: " + e.getMessage());
            model.addAttribute("cities", new PageDTO<CityDTO>());
        }

        return "city";
    }
}