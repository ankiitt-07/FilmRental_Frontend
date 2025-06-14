package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rental")
public class RentalController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RentalController.class);
    private static final int MAX_PAGE_SIZE = 1000; // Reasonable limit for dropdowns

    private final RestTemplate restTemplate;

    @Value("${backend.api.url:http://localhost:8080/api/rental}")
    private String backendApiUrl;

    @Value("${backend.api.customer.url:http://localhost:8080/api/customers}")
    private String customerApiUrl;

    @Value("${backend.api.inventory.url:http://localhost:8080/api/inventory}")
    private String inventoryApiUrl;

    @Value("${backend.api.staff.url:http://localhost:8080/api/staff}")
    private String staffApiUrl;

    public RentalController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // View all rentals
    @GetMapping
    public String listRentals(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model) {
        try {
            String url = backendApiUrl + "/all?page=" + page + "&size=" + size;
            LOGGER.info("Fetching rentals from: {}", url);
            ResponseEntity<PageResponse<RentalDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("rentals", response.getBody().getContent());
                model.addAttribute("currentPage", page);
                model.addAttribute("totalPages", response.getBody().getTotalPages());
                model.addAttribute("size", size);
            } else {
                model.addAttribute("error", "No rentals found");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching rentals: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching rentals: " + e.getMessage());
        }
        return "rental-list";
    }


    // Helper method to load dropdown data
    private void loadDropdowns(Model model) {
        StringBuilder warnings = new StringBuilder();

        try {
            String customersUrl = customerApiUrl + "/all?page=0&size=" + MAX_PAGE_SIZE;
            LOGGER.info("Fetching customers from: {}", customersUrl);
            ResponseEntity<PageResponse<CustomerDTO>> customerResponse = restTemplate.exchange(
                    customersUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (customerResponse.getStatusCode() == HttpStatus.OK && customerResponse.getBody() != null) {
                LOGGER.info("Customers found: {}", customerResponse.getBody().getContent().size());
                model.addAttribute("customers", customerResponse.getBody().getContent());
            } else {
                LOGGER.warn("Unable to load customers");
                model.addAttribute("customers", null);
                warnings.append("Unable to load customers. Proceed without selecting customers. ");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching customers: {}", e.getMessage(), e);
            model.addAttribute("customers", null);
            warnings.append("Error fetching customers: ").append(e.getMessage()).append(" ");
        }

        try {
            String inventoryUrl = inventoryApiUrl + "/all?page=0&size=" + MAX_PAGE_SIZE;
            LOGGER.info("Fetching inventory from: {}", inventoryUrl);
            ResponseEntity<PageResponse<InventoryDTO>> inventoryResponse = restTemplate.exchange(
                    inventoryUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (inventoryResponse.getStatusCode() == HttpStatus.OK && inventoryResponse.getBody() != null) {
                LOGGER.info("Inventory found: {}", inventoryResponse.getBody().getContent().size());
                model.addAttribute("inventories", inventoryResponse.getBody().getContent());
            } else {
                LOGGER.warn("Unable to load inventory");
                model.addAttribute("inventories", null);
                warnings.append("Unable to load inventory. Proceed without selecting inventory. ");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching inventory: {}", e.getMessage(), e);
            model.addAttribute("inventories", null);
            warnings.append("Error fetching inventory: ").append(e.getMessage()).append(" ");
        }

        try {
            String staffUrl = staffApiUrl + "/all?page=0&size=" + MAX_PAGE_SIZE;
            LOGGER.info("Fetching staff from: {}", staffUrl);
            ResponseEntity<PageResponse<StaffDTO>> staffResponse = restTemplate.exchange(
                    staffUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (staffResponse.getStatusCode() == HttpStatus.OK && staffResponse.getBody() != null) {
                LOGGER.info("Staff found: {}", staffResponse.getBody().getContent().size());
                model.addAttribute("staffList", staffResponse.getBody().getContent());
            } else {
                LOGGER.warn("Unable to load staff");
                model.addAttribute("staffList", null);
                warnings.append("Unable to load staff. Proceed without selecting staff. ");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching staff: {}", e.getMessage(), e);
            model.addAttribute("staffList", null);
            warnings.append("Error fetching staff: ").append(e.getMessage()).append(" ");
        }

        if (warnings.length() > 0) {
            model.addAttribute("warning", warnings.toString().trim());
        }
    }

    // Add rental
    @GetMapping("/add")
    public String showAddForm(Model model) {
        LOGGER.info("Showing add rental form");
        model.addAttribute("rental", new RentalDTO());
        loadDropdowns(model);
        return "rental-add";
    }

    @PostMapping("/add")
    public String addRental( @ModelAttribute RentalDTO rentalDTO, BindingResult result, Model model) {
        if (result.hasErrors()) {
            LOGGER.warn("Validation errors in rental data: {}", result.getAllErrors());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", ")));
            loadDropdowns(model);
            return "rental-add";
        }

        try {
            LOGGER.info("Adding rental: {}", rentalDTO);
            String url = backendApiUrl + "/add";
            ResponseEntity<String> response = restTemplate.postForEntity(url, rentalDTO, String.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                LOGGER.info("Rental added successfully");
                return "redirect:/rentals?success=Rental added";
            }
            LOGGER.warn("Add failed with status: {}", response.getStatusCode());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to add rental: " + (response.getBody() != null ? response.getBody() : "Unknown error"));
            loadDropdowns(model);
            return "rental-add";
        } catch (HttpClientErrorException e) {
            LOGGER.error("Client error adding rental: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to add rental: " + e.getResponseBodyAsString());
            loadDropdowns(model);
            return "rental-add";
        } catch (Exception e) {
            LOGGER.error("Unexpected error adding rental: {}", e.getMessage(), e);
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to add rental: " + e.getMessage());
            loadDropdowns(model);
            return "rental-add";
        }
    }

    // Edit rental (fetch)
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid rental ID: {}", id);
            return "redirect:/rentals?error=Invalid rental ID";
        }
        LOGGER.info("Showing edit form for rental ID: {}", id);
        try {
            String url = backendApiUrl + "/" + id;
            LOGGER.info("Fetching rental from: {}", url);
            ResponseEntity<RentalDTO> response = restTemplate.getForEntity(url, RentalDTO.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                LOGGER.info("Rental found: {}", response.getBody());
                model.addAttribute("rental", response.getBody());
                loadDropdowns(model);
                return "rental-edit";
            } else {
                LOGGER.warn("No rental found for ID: {}", id);
                model.addAttribute("error", "No rental found");
                return "redirect:/rentals?error=Rental not found";
            }
        } catch (HttpClientErrorException e) {
            LOGGER.error("Client error fetching rental: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("error", "Error loading rental: " + e.getResponseBodyAsString());
            return "redirect:/rentals?error=Rental not found";
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching rental: {}", e.getMessage(), e);
            model.addAttribute("error", "Error loading rental: " + e.getMessage());
            return "redirect:/rentals?error=Rental not found";
        }
    }

    // Submit edited rental (full update)
    @PostMapping("/edit/full/{id}")
    public String updateRental(@PathVariable Integer id, @Valid @ModelAttribute RentalDTO rentalDTO, BindingResult result, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid rental ID: {}", id);
            return "redirect:/rentals?error=Invalid rental ID";
        }
        if (result.hasErrors()) {
            LOGGER.warn("Validation errors in rental data: {}", result.getAllErrors());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", ")));
            loadDropdowns(model);
            return "rental-edit";
        }

        try {
            LOGGER.info("Updating rental ID: {} with data: {}", id, rentalDTO);
            String url = backendApiUrl + "/update/" + id;
            HttpEntity<RentalDTO> request = new HttpEntity<>(rentalDTO);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Rental updated successfully");
                return "redirect:/rentals?success=Rental updated";
            }
            LOGGER.warn("Update failed with status: {}", response.getStatusCode());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to update rental: " + (response.getBody() != null ? response.getBody() : "Unknown error"));
            loadDropdowns(model);
            return "rental-edit";
        } catch (HttpClientErrorException e) {
            LOGGER.error("Client error updating rental: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to update rental: " + e.getResponseBodyAsString());
            loadDropdowns(model);
            return "rental-edit";
        } catch (Exception e) {
            LOGGER.error("Unexpected error updating rental: {}", e.getMessage(), e);
            model.addAttribute("rental", rentalDTO);
            model.addAttribute("error", "Failed to update rental: " + e.getMessage());
            loadDropdowns(model);
            return "rental-edit";
        }
    }

    // Update return date
    @PostMapping("/return/{id}")
    public String updateReturnDate(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid rental ID: {}", id);
            return "redirect:/rentals?error=Invalid rental ID";
        }
        try {
            LOGGER.info("Updating return date for rental ID: {}", id);
            String url = backendApiUrl + "/update/return/" + id;
            ResponseEntity<RentalDTO> response = restTemplate.exchange(url, HttpMethod.PUT, null, RentalDTO.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                LOGGER.info("Return date updated successfully");
                return "redirect:/rentals?success=Return date updated";
            }
            LOGGER.warn("Return date update failed with status: {}", response.getStatusCode());
            return "redirect:/rentals?error=Return date update failed";
        } catch (HttpClientErrorException e) {
            LOGGER.error("Client error updating return date: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "redirect:/rentals?error=Return date update failed: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            LOGGER.error("Unexpected error updating return date: {}", e.getMessage(), e);
            return "redirect:/rentals?error=Return date update failed: " + e.getMessage();
        }
    }

    // View top ten rented films
    @GetMapping("/toptenfilms")
    public String getTopTenRentedFilms(Model model) {
        try {
            LOGGER.info("Fetching top ten rented films");
            String url = backendApiUrl + "/toptenfilms";
            ResponseEntity<List<TopFilmDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("films", response.getBody());
            } else {
                model.addAttribute("error", "No top films found");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching top films: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching top films: " + e.getMessage());
        }
        return "top-films";
    }

    // View top ten rented films by store
    @GetMapping("/toptenfilms/store/{id}")
    public String getTopTenRentedFilmsByStore(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid store ID: {}", id);
            return "redirect:/rentals?error=Invalid store ID";
        }
        try {
            LOGGER.info("Fetching top ten rented films for store ID: {}", id);
            String url = backendApiUrl + "/toptenfilms/store/" + id;
            ResponseEntity<List<TopFilmDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("films", response.getBody());
                model.addAttribute("storeId", id);
            } else {
                model.addAttribute("error", "No top films found for store");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching top films for store: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching top films for store: " + e.getMessage());
        }
        return "top-films-store";
    }

    // View customers with due rentals by store
    @GetMapping("/due/store/{id}")
    public String getCustomersWithDueRentalsByStore(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid store ID: {}", id);
            return "redirect:/rentals?error=Invalid store ID";
        }
        try {
            LOGGER.info("Fetching customers with due rentals for store ID: {}", id);
            String url = backendApiUrl + "/due/store/" + id;
            ResponseEntity<List<CustomerDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("customers", response.getBody());
                model.addAttribute("storeId", id);
            } else {
                model.addAttribute("error", "No customers with due rentals found");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching customers with due rentals: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching customers with due rentals: " + e.getMessage());
        }
        return "due-customers";
    }

    // View rentals by customer
    @GetMapping("/customer/{id}")
    public String getRentalsByCustomer(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid customer ID: {}", id);
            return "redirect:/rentals?error=Invalid customer ID";
        }
        try {
            LOGGER.info("Fetching rentals for customer ID: {}", id);
            String url = backendApiUrl + "/customer/" + id;
            ResponseEntity<List<RentalDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("rentals", response.getBody());
                model.addAttribute("customerId", id);
            } else {
                model.addAttribute("error", "No rentals found for customer");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching customer rentals: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching customer rentals: " + e.getMessage());
        }
        return "customer-rentals";
    }

    // Get film by rental ID
    @GetMapping("/{id}/film")
    public String getFilmByRentalId(@PathVariable Integer id, Model model) {
        if (id == null || id <= 0) {
            LOGGER.warn("Invalid rental ID: {}", id);
            return "redirect:/rentals?error=Invalid rental ID";
        }
        try {
            LOGGER.info("Fetching film for rental ID: {}", id);
            String url = backendApiUrl + "/" + id + "/film";
            ResponseEntity<FilmDTO> response = restTemplate.getForEntity(url, FilmDTO.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                model.addAttribute("film", response.getBody());
                model.addAttribute("rentalId", id);
            } else {
                model.addAttribute("error", "No film found for rental");
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching film: {}", e.getMessage(), e);
            model.addAttribute("error", "Error fetching film: " + e.getMessage());
        }
        return "rental-film";
    }
}