package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.ActorDTO;
import com.filmrentalfrontend.model.dto.FilmDTO;
import com.filmrentalfrontend.model.dto.LanguageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/films")
public class FilmWebController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilmWebController.class);
    private final RestTemplate restTemplate;

    @Value("${backend.api.url:http://localhost:8080/api/films}")
    private String backendApiUrl;

    public FilmWebController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    @GetMapping
    public String listFilms(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        try {
            String url = backendApiUrl + "/all?page=" + page + "&size=" + size;
            LOGGER.info("Fetching films from: {}", url);
            ResponseEntity<PageResponse<FilmDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            LOGGER.info("Response status: {}, body: {}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                LOGGER.info("Films found: {}", response.getBody().getContent().size());
                model.addAttribute("films", response.getBody().getContent());
                model.addAttribute("currentPage", page);
                model.addAttribute("totalPages", response.getBody().getTotalPages());
                model.addAttribute("size", size); // Add this line to pass size to the model
            } else {
                LOGGER.warn("No films found or error occurred");
                model.addAttribute("films", null);
                model.addAttribute("error", "No films available or error occurred");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching films", e);
            model.addAttribute("films", null);
            model.addAttribute("error", "Error fetching films: " + e.getMessage());
        }
        return "film-list";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        try {

            String url = backendApiUrl + "/all?page=0&size=" + Integer.MAX_VALUE;
            LOGGER.info("Fetching film with ID: {} from URL: {}", id, url);
            ResponseEntity<PageResponse<FilmDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<PageResponse<FilmDTO>>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                FilmDTO film = response.getBody().getContent().stream()
                        .filter(f -> f.getFilmId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (film != null) {
                    LOGGER.info("Film found: {}", film);
                    model.addAttribute("film", film);
                    return "film-edit";
                }
                LOGGER.warn("Film not found for ID: {}", id);
            }
            return "redirect:/films?error=Film not found";
        } catch (Exception e) {
            LOGGER.error("Error fetching film with ID: {}", id, e);
            return "redirect:/films?error=Error fetching film: " + e.getMessage();
        }
    }

    @PostMapping("/update/{id}")
    public String updateFilm(@PathVariable Integer id, @ModelAttribute FilmDTO filmDto) {
        try {
            LOGGER.info("Updating film ID: {} with data: {}", id, filmDto);
            String titleUrl = backendApiUrl + "/update/title/" + id;
            ResponseEntity<FilmDTO> titleResponse = restTemplate.exchange(
                    titleUrl, HttpMethod.PUT, new HttpEntity<>(filmDto.getTitle()), FilmDTO.class);

            String yearUrl = backendApiUrl + "/update/releaseyear/" + id;
            ResponseEntity<FilmDTO> yearResponse = restTemplate.exchange(
                    yearUrl, HttpMethod.PUT, new HttpEntity<>(filmDto.getReleaseYear()), FilmDTO.class);

            String durationUrl = backendApiUrl + "/update/rentalduration/" + id;
            ResponseEntity<FilmDTO> durationResponse = restTemplate.exchange(
                    durationUrl, HttpMethod.PUT, new HttpEntity<>(filmDto.getRentalDuration()), FilmDTO.class);

            String rateUrl = backendApiUrl + "/update/rentalrate/" + id;
            ResponseEntity<FilmDTO> rateResponse = restTemplate.exchange(
                    rateUrl, HttpMethod.PUT, new HttpEntity<>(filmDto.getRentalRate()), FilmDTO.class);

            if (titleResponse.getStatusCode() == HttpStatus.OK &&
                    yearResponse.getStatusCode() == HttpStatus.OK &&
                    durationResponse.getStatusCode() == HttpStatus.OK &&
                    rateResponse.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Film ID: {} updated successfully", id);
                return "redirect:/films?success=Updated successfully";
            }
            LOGGER.warn("Update failed for film ID: {}", id);
            return "redirect:/films?error=Update failed";
        } catch (Exception e) {
            LOGGER.error("Error updating film ID: {}", id, e);
            return "redirect:/films?error=Update failed: " + e.getMessage();
        }
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        LOGGER.info("Showing add film form");
        FilmDTO filmDto = new FilmDTO();
        filmDto.setReplacementCost(new BigDecimal("19.99")); // Default value
        model.addAttribute("film", filmDto);

        try {
            String actorsUrl = backendApiUrl.replace("/films", "/actors/all?page=0&size=" + Integer.MAX_VALUE);
            LOGGER.info("Fetching actors from: {}", actorsUrl);
            ResponseEntity<PageResponse<ActorDTO>> actorsResponse = restTemplate.exchange(
                    actorsUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (actorsResponse.getStatusCode() == HttpStatus.OK && actorsResponse.getBody() != null) {
                LOGGER.info("Actors found: {}", actorsResponse.getBody().getContent().size());
                model.addAttribute("actors", actorsResponse.getBody().getContent());
            } else {
                LOGGER.warn("Unable to load actors");
                model.addAttribute("actors", null);
                model.addAttribute("warning", "Unable to load actors. Proceed without selecting actors.");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching actors", e);
            model.addAttribute("actors", null);
            model.addAttribute("warning", "Unable to load actors: " + e.getMessage());
        }

        try {
            String languagesUrl = backendApiUrl.replace("/films", "/languages/all?page=0&size=" + Integer.MAX_VALUE);
            LOGGER.info("Fetching languages from: {}", languagesUrl);
            ResponseEntity<PageResponse<LanguageDTO>> languagesResponse = restTemplate.exchange(
                    languagesUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (languagesResponse.getStatusCode() == HttpStatus.OK && languagesResponse.getBody() != null) {
                LOGGER.info("Languages found: {}", languagesResponse.getBody().getContent().size());
                model.addAttribute("languages", languagesResponse.getBody().getContent());
            } else {
                LOGGER.warn("Unable to load languages");
                model.addAttribute("languages", null);
                model.addAttribute("warning", "Unable to load languages. Please enter a valid language ID manually.");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching languages", e);
            model.addAttribute("languages", null);
            model.addAttribute("warning", "Error fetching languages: " + e.getMessage());
        }

        return "film-add";
    }

    @PostMapping("/add")
    public String addFilm(@ModelAttribute FilmDTO filmDto, Model model) {
        if (filmDto == null || filmDto.getTitle() == null || filmDto.getLanguageId() == null || filmDto.getReplacementCost() == null) {
            LOGGER.warn("Invalid film data submitted: {}", filmDto);
            model.addAttribute("film", filmDto != null ? filmDto : new FilmDTO());
            model.addAttribute("error", "Title, language ID, and replacement cost are required");
            return "film-add";
        }

        try {
            LOGGER.info("Adding film: {}", filmDto);
            String url = backendApiUrl;
            ResponseEntity<String> response = restTemplate.postForEntity(url, filmDto, String.class);
            LOGGER.info("Response status: {}, body: {}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode() == HttpStatus.CREATED) {
                LOGGER.info("Film added successfully");
                return "redirect:/films?success=Film added successfully";
            }
            LOGGER.warn("Add failed with status: {}", response.getStatusCode());
            model.addAttribute("film", filmDto);
            model.addAttribute("error", "Failed to add film: " + response.getBody());
            return "film-add";
        } catch (HttpClientErrorException e) {
            LOGGER.error("Client error adding film: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("film", filmDto);
            model.addAttribute("error", "Failed to add film: " + e.getResponseBodyAsString());
            return "film-add";
        } catch (Exception e) {
            LOGGER.error("Unexpected error adding film: {}", e.getMessage(), e);
            model.addAttribute("film", filmDto);
            model.addAttribute("error", "Failed to add film: " + e.getMessage());
            return "film-add";
        }
    }

    public static class PageResponse<T> {
        private List<T> content;
        private int totalPages;

        public List<T> getContent() { return content; }
        public void setContent(List<T> content) { this.content = content; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }
}