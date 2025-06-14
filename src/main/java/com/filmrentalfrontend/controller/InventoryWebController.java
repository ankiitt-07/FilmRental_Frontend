package com.filmrentalfrontend.controller;

import com.filmrentalfrontend.model.dto.InventoryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/inventory")
public class InventoryWebController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${backend.api.url.inventory:http://localhost:8080/api/inventory}")
    private String backendApiUrl;

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
            String url = backendApiUrl + "/all?page=" + page + "&size=" + size;
            System.out.println("Calling API: " + url); // Debug
            ResponseEntity<PageResponse<InventoryDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponse<InventoryDTO>>() {}
            );

            System.out.println("API Response Status: " + response.getStatusCode()); // Debug
            List<InventoryDTO> inventories = Collections.emptyList();
            Set<Integer> storeIds = Collections.emptySet();
            int totalPages = 0;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                inventories = response.getBody().getContent();
                totalPages = response.getBody().getTotalPages();
                System.out.println("Inventories Count: " + inventories.size()); // Debug
                System.out.println("Inventories: " + inventories); // Debug

                if (!inventories.isEmpty()) {
                    storeIds = inventories.stream()
                            .filter(inventory -> inventory.getStoreId() != null)
                            .map(InventoryDTO::getStoreId)
                            .collect(Collectors.toSet());
                }
                System.out.println("Store IDs: " + storeIds); // Debug
            } else {
                System.out.println("API returned no data or error"); // Debug
                model.addAttribute("error", "No inventories found or API error");
            }

            model.addAttribute("inventories", inventories);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("storeIds", storeIds);

            // Temporary fallback for testing
            if (storeIds.isEmpty()) {
                System.out.println("Using fallback storeIds: [1, 2]"); // Debug
                model.addAttribute("storeIds", Set.of(1, 2));
            }

        } catch (Exception e) {
            System.out.println("Error fetching inventories: " + e.getMessage()); // Debug
            e.printStackTrace();
            model.addAttribute("inventories", Collections.emptyList());
            model.addAttribute("storeIds", Set.of(1, 2)); // Fallback
            model.addAttribute("error", "Error fetching inventories: " + e.getMessage());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
        }
        return "inventory-list";
    }

    @GetMapping("/inventories/add")
    public String showAddForm(Model model) {
        InventoryDTO inventoryDTO = new InventoryDTO();
        model.addAttribute("inventory", inventoryDTO);
        return "inventory-add";
    }

    @PostMapping("/inventories/add")
    public String addInventory(@ModelAttribute InventoryDTO inventoryDTO, Model model) {
        try {
            // Ensure inventoryId is null for new records
            inventoryDTO.setInventoryId(null);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<InventoryDTO> request = new HttpEntity<>(inventoryDTO, headers);
            String url = backendApiUrl + "/add";
            System.out.println("Calling POST API: " + url + " with payload: " + inventoryDTO); // Debug

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            System.out.println("API Response Status: " + response.getStatusCode() + ", Body: " + response.getBody()); // Debug
            if (response.getStatusCode() == HttpStatus.CREATED) {
                return "redirect:/inventories?success=Inventory added successfully";
            } else {
                String errorMessage = response.getBody() != null ? (String) response.getBody().getOrDefault("error", response.getBody().toString()) : "Unknown error";
                model.addAttribute("error", "Failed to add inventory: " + errorMessage);
                model.addAttribute("inventory", inventoryDTO);
                return "inventory-add";
            }
        } catch (HttpClientErrorException e) {
            System.out.println("HTTP Error adding inventory: " + e.getStatusCode() + ", Response: " + e.getResponseBodyAsString()); // Debug
            String errorMessage = e.getResponseBodyAsString();
            try {
                // Parse JSON error response
                Map errorMap = objectMapper.readValue(errorMessage, Map.class);
                errorMessage = (String) errorMap.getOrDefault("error", errorMessage);
            } catch (Exception ignored) {}
            model.addAttribute("error", "Error adding inventory: " + errorMessage);
            model.addAttribute("inventory", inventoryDTO);
            return "inventory-add";
        } catch (Exception e) {
            System.out.println("Error adding inventory: " + e.getMessage()); // Debug
            e.printStackTrace();
            model.addAttribute("error", "Error adding inventory: " + e.getMessage());
            model.addAttribute("inventory", inventoryDTO);
            return "inventory-add";
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
//InventoryWebController