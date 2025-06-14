package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.StaffDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/staff")
public class StaffWebController {

    private final RestTemplate restTemplate;
    private static final Logger LOGGER = Logger.getLogger(StaffWebController.class.getName());
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

    @Value("${backend.staff.api.url:http://localhost:8080/api/staff}")
    private String backendStaffApiUrl;

    public StaffWebController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String listStaff(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        try {
            String url = backendStaffApiUrl + "/all?page=" + page + "&size=" + size;
            LOGGER.info("Fetching staff from: " + url);
            ResponseEntity<PageResponse<StaffDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponse<StaffDTO>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("staff", response.getBody().getContent());
                model.addAttribute("currentPage", page);
                model.addAttribute("totalPages", response.getBody().getTotalPages());
            } else {
                model.addAttribute("staff", null);
                model.addAttribute("error", "No staff found or error occurred");
            }
        } catch (Exception e) {
            LOGGER.severe("Error fetching staff: " + e.getMessage());
            model.addAttribute("staff", null);
            model.addAttribute("error", "Error fetching staff: " + e.getMessage());
        }
        return "staff-list";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        try {
            String url = backendStaffApiUrl + "/all?page=0&size=" + Integer.MAX_VALUE;
            LOGGER.info("Fetching staff for edit from: " + url);
            ResponseEntity<PageResponse<StaffDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponse<StaffDTO>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                StaffDTO staff = response.getBody().getContent().stream()
                        .filter(s -> s.getStaffId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (staff != null) {
                    model.addAttribute("staff", staff);
                    return "staff-edit";
                }
            }
            return "redirect:/staff?error=Staff not found";
        } catch (Exception e) {
            LOGGER.severe("Error fetching staff for edit: " + e.getMessage());
            return "redirect:/staff?error=Error fetching staff: " + e.getMessage();
        }
    }

    @PostMapping("/update/{id}")
    public String updateStaff(@PathVariable Integer id, @ModelAttribute StaffDTO staffDTO, Model model) {
        try {
            LOGGER.info("Updating staff ID: " + id);
            // Validate inputs
            if (staffDTO.getFirstName() == null || staffDTO.getFirstName().trim().isEmpty()) {
                throw new IllegalArgumentException("First name cannot be empty");
            }
            if (staffDTO.getLastName() == null || staffDTO.getLastName().trim().isEmpty()) {
                throw new IllegalArgumentException("Last name cannot be empty");
            }
            if (staffDTO.getEmail() == null || staffDTO.getEmail().trim().isEmpty()) {
                throw new IllegalArgumentException("Email cannot be empty");
            }
            if (!Pattern.matches(EMAIL_REGEX, staffDTO.getEmail())) {
                throw new IllegalArgumentException("Invalid email format");
            }
            if (staffDTO.getActive() == null) {
                throw new IllegalArgumentException("Active status cannot be empty");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Update first name
            String fnUrl = backendStaffApiUrl + "/update/fn/" + id;
            HttpEntity<String> fnEntity = new HttpEntity<>(staffDTO.getFirstName().trim(), headers);
            ResponseEntity<StaffDTO> fnResponse = restTemplate.exchange(
                    fnUrl,
                    HttpMethod.PUT,
                    fnEntity,
                    StaffDTO.class
            );

            // Update last name
            String lnUrl = backendStaffApiUrl + "/update/ln/" + id;
            HttpEntity<String> lnEntity = new HttpEntity<>(staffDTO.getLastName().trim(), headers);
            ResponseEntity<StaffDTO> lnResponse = restTemplate.exchange(
                    lnUrl,
                    HttpMethod.PUT,
                    lnEntity,
                    StaffDTO.class
            );

            // Update email
            String emailUrl = backendStaffApiUrl + "/update/email/" + id;
            HttpEntity<String> emailEntity = new HttpEntity<>(staffDTO.getEmail().trim(), headers);
            ResponseEntity<StaffDTO> emailResponse;
            try {
                emailResponse = restTemplate.exchange(
                        emailUrl,
                        HttpMethod.PUT,
                        emailEntity,
                        StaffDTO.class
                );
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.BAD_REQUEST && e.getResponseBodyAsString().contains("Email already exists")) {
                    model.addAttribute("staff", staffDTO);
                    model.addAttribute("error", "Email already exists");
                    return "staff-edit";
                }
                throw e;
            }

            // Update active status
            String activeUrl = backendStaffApiUrl + "/update/active/" + id;
            HttpEntity<Boolean> activeEntity = new HttpEntity<>(staffDTO.getActive(), headers);
            ResponseEntity<StaffDTO> activeResponse = restTemplate.exchange(
                    activeUrl,
                    HttpMethod.PUT,
                    activeEntity,
                    StaffDTO.class
            );

            if (fnResponse.getStatusCode() == HttpStatus.OK &&
                    lnResponse.getStatusCode() == HttpStatus.OK &&
                    emailResponse.getStatusCode() == HttpStatus.OK &&
                    activeResponse.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Staff updated successfully for ID: " + id);
                return "redirect:/staff?success=Updated successfully";
            }
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Update failed");
            return "staff-edit";
        } catch (HttpClientErrorException e) {
            LOGGER.severe("HTTP error updating staff: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Update failed: " + e.getResponseBodyAsString().replaceAll("[\"{}]", ""));
            return "staff-edit";
        } catch (IllegalArgumentException e) {
            LOGGER.severe("Validation error updating staff: " + e.getMessage());
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Update failed: " + e.getMessage());
            return "staff-edit";
        } catch (Exception e) {
            LOGGER.severe("Unexpected error updating staff: " + e.getMessage());
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Update failed: " + e.getMessage().replaceAll("[\"{}]", ""));
            return "staff-edit";
        }
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        StaffDTO staffDTO = new StaffDTO();
        model.addAttribute("staff", staffDTO);
        return "staff-add";
    }

    @PostMapping("/add")
    public String addStaff(@ModelAttribute StaffDTO staffDTO, Model model) {
        try {
            String url = backendStaffApiUrl + "/add";
            LOGGER.info("Adding new staff to: " + url);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<StaffDTO> entity = new HttpEntity<>(staffDTO, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                LOGGER.info("Staff added successfully");
                return "redirect:/staff?success=Staff added successfully";
            }
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Add failed");
            return "staff-add";
        } catch (HttpClientErrorException e) {
            LOGGER.severe("HTTP error adding staff: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Add failed: " + e.getResponseBodyAsString().replaceAll("[\"{}]", ""));
            return "staff-add";
        } catch (Exception e) {
            LOGGER.severe("Error adding staff: " + e.getMessage());
            model.addAttribute("staff", staffDTO);
            model.addAttribute("error", "Add failed: " + e.getMessage());
            return "staff-add";
        }
    }

    public static class PageResponse<T> {
        private List<T> content;
        private int totalPages;

        public List<T> getContent() {
            return content;
        }

        public void setContent(List<T> content) {
            this.content = content;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
    }
}