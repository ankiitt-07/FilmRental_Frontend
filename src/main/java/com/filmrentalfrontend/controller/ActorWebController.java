package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.ActorDTO;
import com.filmrentalfrontend.model.dto.FilmDTO;
import com.filmrentalfrontend.model.dto.PageResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/actors")
public class ActorWebController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorWebController.class);
    private final RestTemplate restTemplate;

    @Value("${backend.api.url:http://localhost:8080/api/actors}")
    private String backendApiUrl;

    public ActorWebController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String listActors(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        try {
            String url = backendApiUrl + "/all?page=" + page + "&size=" + size;
            LOGGER.info("Fetching actors from: {}", url);
            ResponseEntity<PageResponse<ActorDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<PageResponse<ActorDTO>>() {});
            LOGGER.info("Response status: {}, body: {}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                LOGGER.info("Actors found: {}", response.getBody().getContent().size());
                model.addAttribute("actors", response.getBody().getContent());
                model.addAttribute("currentPage", page);
                model.addAttribute("totalPages", response.getBody().getTotalPages());
            } else {
                LOGGER.warn("No actors found or error occurred");
                model.addAttribute("actors", null);
                model.addAttribute("error", "No actors found or error occurred");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching actors", e);
            model.addAttribute("actors", null);
            model.addAttribute("error", "Error fetching actors: " + e.getMessage());
        }
        return "actor-list";
    }

    @GetMapping("/actors/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        try {
            String url = backendApiUrl + "/all?page=0&size=" + Integer.MAX_VALUE;
            LOGGER.info("Fetching actor with ID: {} from URL: {}", id, url);
            ResponseEntity<PageResponse<ActorDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<PageResponse<ActorDTO>>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ActorDTO actor = response.getBody().getContent().stream()
                        .filter(a -> a.getActorId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (actor != null) {
                    LOGGER.info("Actor found: {}", actor);
                    model.addAttribute("actor", actor);
                    return "actor-edit";
                }
                LOGGER.warn("Actor not found for ID: {}", id);
            }
            return "redirect:/actors?error=Actor not found";
        } catch (Exception e) {
            LOGGER.error("Error fetching actor with ID: {}", id, e);
            return "redirect:/actors?error=Error fetching actor: " + e.getMessage();
        }
    }

    @PostMapping("/actors/update/{id}")
    public String updateActor(@PathVariable Integer id, @ModelAttribute ActorDTO actorDTO) {
        try {
            LOGGER.info("Updating actor ID: {} with data: {}", id, actorDTO);
            String firstNameUrl = backendApiUrl + "/update/firstname/" + id;
            ResponseEntity<ActorDTO> firstNameResponse = restTemplate.exchange(
                    firstNameUrl, HttpMethod.PUT, new HttpEntity<>(actorDTO.getFirstName()), ActorDTO.class);
            String lastNameUrl = backendApiUrl + "/update/lastname/" + id;
            ResponseEntity<ActorDTO> lastNameResponse = restTemplate.exchange(
                    lastNameUrl, HttpMethod.PUT, new HttpEntity<>(actorDTO.getLastName()), ActorDTO.class);
            if (firstNameResponse.getStatusCode() == HttpStatus.OK && lastNameResponse.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Actor ID: {} updated successfully", id);
                return "redirect:/actors?success=Updated successfully";
            }
            LOGGER.warn("Update failed for actor ID: {}", id);
            return "redirect:/actors?error=Update failed";
        } catch (Exception e) {
            LOGGER.error("Error updating actor ID: {}", id, e);
            return "redirect:/actors?error=Update failed: " + e.getMessage();
        }
    }

    @GetMapping("/actors/add")
    public String showAddForm(Model model) {
        LOGGER.info("Showing add actor form");
        ActorDTO actorDTO = new ActorDTO();
        model.addAttribute("actor", actorDTO);
        return "actor-add";
    }

    @PostMapping("/actors/add")
    public String addActor(@ModelAttribute ActorDTO actorDTO) {
        try {
            LOGGER.info("Adding actor: {}", actorDTO);
            String url = backendApiUrl + "/post";
            ResponseEntity<String> response = restTemplate.postForEntity(url, actorDTO, String.class);
            LOGGER.info("Response status: {}, body: {}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode() == HttpStatus.CREATED) {
                return "redirect:/actors?success=Actor added successfully";
            }
            LOGGER.warn("Add failed with status: {}", response.getStatusCode());
            return "redirect:/actors?error=Add failed";
        } catch (Exception e) {
            LOGGER.error("Error adding actor", e);
            return "redirect:/actors?error=Add failed: " + e.getMessage();
        }
    }

    @GetMapping("/actors/search")
    public String searchActors(@RequestParam(required = false) String firstName,
                               @RequestParam(required = false) String lastName,
                               Model model) {
        try {
            // Validate input
            if ((firstName == null || firstName.trim().isEmpty()) &&
                    (lastName == null || lastName.trim().isEmpty())) {
                LOGGER.warn("Search attempted without firstName or lastName");
                model.addAttribute("error", "Please provide at least actor-list.html first name or last name");
                model.addAttribute("actors", null);
                return "actor-list";
            }

            List<ActorDTO> actors = new ArrayList<>();

            // Search by firstName if provided
            if (firstName != null && !firstName.trim().isEmpty()) {
                String encodedFirstName = URLEncoder.encode(firstName.trim(), StandardCharsets.UTF_8);
                String url = backendApiUrl + "/firstname/" + encodedFirstName;
                LOGGER.info("Searching actors by firstName with URL: {}", url);
                try {
                    ResponseEntity<List<ActorDTO>> response = restTemplate.exchange(
                            url, HttpMethod.GET, null, new ParameterizedTypeReference<List<ActorDTO>>() {
                            });
                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        actors.addAll(response.getBody());
                        LOGGER.info("Found {} actors for firstName: {}", response.getBody().size(), firstName);
                    } else {
                        LOGGER.warn("No actors found for firstName: {}", firstName);
                    }
                } catch (HttpClientErrorException e) {
                    LOGGER.warn("Client error for firstName search ({}): status {}, message {}",
                            firstName, e.getStatusCode(), e.getMessage());
                }
            }

            // Search by lastName if provided
            if (lastName != null && !lastName.trim().isEmpty()) {
                String encodedLastName = URLEncoder.encode(lastName.trim(), StandardCharsets.UTF_8);
                String url = backendApiUrl + "/lastname/" + encodedLastName;
                LOGGER.info("Searching actors by lastName with URL: {}", url);
                try {
                    ResponseEntity<List<ActorDTO>> response = restTemplate.exchange(
                            url, HttpMethod.GET, null, new ParameterizedTypeReference<List<ActorDTO>>() {
                            });
                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        // Add only non-duplicate actors
                        for (ActorDTO actor : response.getBody()) {
                            if (actors.stream().noneMatch(a -> a.getActorId().equals(actor.getActorId()))) {
                                actors.add(actor);
                            }
                        }
                        LOGGER.info("Found {} actors for lastName: {}", response.getBody().size(), lastName);
                    } else {
                        LOGGER.warn("No actors found for lastName: {}", lastName);
                    }
                } catch (HttpClientErrorException e) {
                    LOGGER.warn("Client error for lastName search ({}): status {}, message {}",
                            lastName, e.getStatusCode(), e.getMessage());
                }
            }

            if (!actors.isEmpty()) {
                LOGGER.info("Total found {} actors for search: firstName={}, lastName={}",
                        actors.size(), firstName, lastName);
                model.addAttribute("actors", actors);
                model.addAttribute("currentPage", 0);
                model.addAttribute("totalPages", 1);
            } else {
                LOGGER.warn("No actors found for search: firstName={}, lastName={}", firstName, lastName);
                String url;
                if (firstName != null && !firstName.trim().isEmpty()) {
                    url = backendApiUrl + "/firstname/" + firstName;
                } else if (lastName != null && !lastName.trim().isEmpty()) {
                    url = backendApiUrl + "/lastname/" + lastName;
                } else {
                    LOGGER.warn("Search attempted without firstName or lastName");
                    return "redirect:/actors?error=Please provide a first name or last name";
                }
                LOGGER.info("Searching actors with URL: {}", url);
                ResponseEntity<List<ActorDTO>> response = restTemplate.exchange(
                        url, HttpMethod.GET, null, new ParameterizedTypeReference<List<ActorDTO>>() {
                        });
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    LOGGER.info("Found {} actors", response.getBody().size());
                    model.addAttribute("actors", response.getBody());
                } else {
                    LOGGER.warn("No actors found for search");
                    model.addAttribute("actors", null);
                    model.addAttribute("error", "No actors found");
                }
            }
        }
        catch(Exception e){
                LOGGER.error("Unexpected error searching actors: firstName={}, lastName={}, error={}",
                        firstName, lastName, e.getMessage(), e);
                LOGGER.error("Error searching actors", e);
                model.addAttribute("actors", null);
                model.addAttribute("error", "Error searching actors: " + e.getMessage());
            }
            return "actor-list";
        }

        @GetMapping("/actors/{id}/films")
        public String listFilmsByActor (@PathVariable Integer id, Model model){
            try {
                String url = backendApiUrl + "/" + id + "/films";
                LOGGER.info("Fetching films for actor ID: {} from URL: {}", id, url);
                ResponseEntity<List<FilmDTO>> response = restTemplate.exchange(
                        url, HttpMethod.GET, null, new ParameterizedTypeReference<List<FilmDTO>>() {
                        });
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    LOGGER.info("Films found: {}", response.getBody().size());
                    model.addAttribute("films", response.getBody());
                    model.addAttribute("actorId", id);
                } else {
                    LOGGER.warn("No films found for actor ID: {}", id);
                    model.addAttribute("films", null);
                    model.addAttribute("error", "No films found for this actor");
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching films for actor ID: {}", id, e);
                model.addAttribute("films", null);
                model.addAttribute("error", "Error fetching films: " + e.getMessage());
            }
            return "actor-films";
        }

        @GetMapping("/actors/assign-film/{id}")
        public String showAssignFilmForm (@PathVariable Integer id, Model model){
            try {
                String filmsUrl = backendApiUrl.replace("/actors", "/films/all?page=0&size=" + Integer.MAX_VALUE);
                LOGGER.info("Fetching films from URL: {}", filmsUrl);
                ResponseEntity<PageResponse<FilmDTO>> filmsResponse = restTemplate.exchange(
                        filmsUrl, HttpMethod.GET, null, new ParameterizedTypeReference<PageResponse<FilmDTO>>() {
                        });
                if (filmsResponse.getStatusCode() == HttpStatus.OK && filmsResponse.getBody() != null) {
                    LOGGER.info("Films found: {}", filmsResponse.getBody().getContent().size());
                    model.addAttribute("films", filmsResponse.getBody().getContent());
                    model.addAttribute("actorId", id);
                } else {
                    LOGGER.warn("Unable to load films");
                    model.addAttribute("films", null);
                    model.addAttribute("warning", "Unable to load films. Proceed without selecting films.");
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching films", e);
                model.addAttribute("films", null);
                model.addAttribute("warning", "Error fetching films: " + e.getMessage());
            }
            return "actor-assign-film";
        }

        @PostMapping("/actors/assign-film/{id}")
        public String assignFilmToActor (@PathVariable Integer id, @RequestParam Integer filmId){
            try {
                LOGGER.info("Assigning film ID: {} to actor ID: {}", filmId, id);
                String url = backendApiUrl + "/" + id + "/film";
                ResponseEntity<List<FilmDTO>> response = restTemplate.exchange(
                        url, HttpMethod.PUT, new HttpEntity<>(filmId), new ParameterizedTypeReference<List<FilmDTO>>() {
                        });
                if (response.getStatusCode() == HttpStatus.OK) {
                    LOGGER.info("Film assigned successfully");
                    return "redirect:/actors/" + id + "/films?success=Film assigned successfully";
                }
                LOGGER.warn("Assign failed for actor ID: {}", id);
                return "redirect:/actors/" + id + "/films?error=Assign failed";
            } catch (Exception e) {
                LOGGER.error("Error assigning film to actor ID: {}", id, e);
                return "redirect:/actors/" + id + "/films?error=Assign failed: " + e.getMessage();
            }
        }

        @GetMapping("/actors/top-ten")
        public String listTopTenActors (Model model){
            try {
                String url = backendApiUrl + "/toptenbyfilmcount";
                LOGGER.info("Fetching top ten actors from URL: {}", url);
                ResponseEntity<List<Object[]>> response = restTemplate.exchange(
                        url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Object[]>>() {
                        });
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    LOGGER.info("Top actors found: {}", response.getBody().size());
                    model.addAttribute("topActors", response.getBody());
                } else {
                    LOGGER.warn("No top actors found");
                    model.addAttribute("topActors", null);
                    model.addAttribute("error", "No top actors found");
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching top actors", e);
                model.addAttribute("topActors", null);
                model.addAttribute("error", "Error fetching top actors: " + e.getMessage());
            }
            return "actor-top-ten";
        }
    }