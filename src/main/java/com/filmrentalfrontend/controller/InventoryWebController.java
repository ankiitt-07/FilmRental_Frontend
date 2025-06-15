package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.InventoryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/inventory")
public class InventoryWebController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryWebController.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${backend.api.url.inventory:http://localhost:8080/api/inventory}")
    private String backendApiUrl;

    @Value("${backend.health.check.enabled:false}")
    private boolean healthCheckEnabled;

    @Autowired
    public InventoryWebController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String listInventories(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  Model model) {
        try {
            // Check backend health if enabled
            if (healthCheckEnabled && !isBackendHealthy()) {
                logger.warn("Backend service may be unavailable at {}. Proceeding with API call.", backendApiUrl);
                model.addAttribute("warning", "Backend service health check failed. Results may be incomplete.");
            }

            String url = backendApiUrl + "/all?page=" + page + "&size=" + size;
            logger.info("Calling API: {}", url);

            ResponseEntity<PageResponse<InventoryDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<PageResponse<InventoryDTO>>() {});

            logger.info("API Response Status: {}", response.getStatusCode());

            // Log raw response for debugging
            if (response.getBody() != null) {
                logger.debug("Raw API Response: {}", objectMapper.writeValueAsString(response.getBody()));
            }

            List<InventoryDTO> inventories = Collections.emptyList();
            Set<Integer> storeIds = Collections.emptySet();
            int totalPages = 0;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getContent() != null) {
                inventories = response.getBody().getContent();
                totalPages = response.getBody().getTotalPages();
                logger.info("Inventories Count: {}, Total Pages: {}", inventories.size(), totalPages);

                // Log InventoryDTO objects for debugging
                inventories.forEach(inventory ->
                        logger.debug("Inventory: id={}, filmId={}, store={}",
                                inventory.getInventoryId(),
                                inventory.getFilmId(),
                                inventory.getStore() != null ? inventory.getStore().getStoreId() : "null")
                );

                if (!inventories.isEmpty()) {
                    storeIds = inventories.stream()
                            .filter(inventory -> inventory.getStore() != null && inventory.getStore().getStoreId() != null)
                            .map(inventory -> inventory.getStore().getStoreId())
                            .collect(Collectors.toSet());
                }

                if (storeIds.isEmpty()) {
                    logger.warn("No valid store IDs found in inventory data. Using fallback: [1, 2]");
                    storeIds = Set.of(1, 2);
                }
                logger.debug("Store IDs: {}", storeIds);
            } else {
                logger.warn("API returned no data or error: {}", response.getStatusCode());
                model.addAttribute("error", "No inventories found or API error");
            }

            model.addAttribute("inventories", inventories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("storeIds", storeIds);

        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error fetching inventories: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            model.addAttribute("error", "Error fetching inventories: " + e.getResponseBodyAsString());
            model.addAttribute("inventories", Collections.emptyList());
            model.addAttribute("storeIds", Set.of(1, 2));
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
        } catch (Exception e) {
            logger.error("Unexpected error fetching inventories: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to connect to backend: " + e.getMessage());
            return "error";
        }
        return "inventory-list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("inventory", new InventoryDTO());
        return "inventory-add";
    }

    @PostMapping("/add")
    public String addInventory(@ModelAttribute InventoryDTO inventoryDTO, Model model) {
        try {
            // Check backend health if enabled
            if (healthCheckEnabled && !isBackendHealthy()) {
                logger.warn("Backend service may be unavailable at {}. Proceeding with API call.", backendApiUrl);
                model.addAttribute("warning", "Backend service health check failed. Operation may fail.");
            }

            inventoryDTO.setInventoryId(null); // Ensure inventoryId is null for new records
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<InventoryDTO> request = new HttpEntity<>(inventoryDTO, headers);
            String url = backendApiUrl + "/add";
            logger.info("Calling POST API: {} with payload: {}", url, inventoryDTO);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            logger.info("API Response Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode() == HttpStatus.CREATED) {
                return "redirect:/inventory?success=Inventory added successfully";
            } else {
                String errorMessage = response.getBody() != null ? (String) response.getBody().getOrDefault("error", "Unknown error") : "Unknown error";
                model.addAttribute("error", "Failed to add inventory: " + errorMessage);
                model.addAttribute("inventory", inventoryDTO);
                return "inventory-add";
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error adding inventory: Status {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            String errorMessage = e.getResponseBodyAsString();
            try {
                Map errorMap = objectMapper.readValue(errorMessage, Map.class);
                errorMessage = (String) errorMap.getOrDefault("error", errorMessage);
            } catch (Exception ignored) {}
            model.addAttribute("error", "Error adding inventory: " + errorMessage);
            model.addAttribute("inventory", inventoryDTO);
            return "inventory-add";
        } catch (Exception e) {
            logger.error("Unexpected error adding inventory: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to connect to backend: " + e.getMessage());
            return "error";
        }
    }

    // Check backend health
    private boolean isBackendHealthy() {
        try {
            ResponseEntity<String> healthResponse = restTemplate.getForEntity(backendApiUrl + "/health", String.class);
            logger.debug("Backend health check response: {}", healthResponse.getStatusCode());
            return healthResponse.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            logger.warn("Backend health check failed: {}", e.getMessage());
            return false;
        }
    }

    // Global exception handler
    @ExceptionHandler(Exception.class)
    public String handleUnexpectedException(Exception e, Model model) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
        return "error";
    }

    public static class PageResponse<T> {
        private List<T> content;
        private int totalPages;
        private long totalElements;
        private int size;
        private int number;

        public List<T> getContent() { return content; }
        public void setContent(List<T> content) { this.content = content; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
    }
}