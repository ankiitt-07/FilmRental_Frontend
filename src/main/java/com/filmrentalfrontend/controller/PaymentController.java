package com.filmrentalfrontend.controller;


import com.filmrentalfrontend.model.dto.PageResponseDTO;
import com.filmrentalfrontend.model.dto.PaymentDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final String BASE_URL = "http://localhost:8080/api/payments";

    @GetMapping
    public String getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        try {
            logger.info("Fetching payments for page {} with size {}", page, size);
            String url = BASE_URL + "/all?page=" + page + "&size=" + size;
            ResponseEntity<PageResponseDTO<PaymentDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponseDTO<PaymentDTO>>() {}
            );
            PageResponseDTO<PaymentDTO> pageResponse = response.getBody();
            if (pageResponse == null) {
                throw new Exception("No response from backend");
            }
            List<PaymentDTO> payments = pageResponse.getContent();
            model.addAttribute("payments", payments != null ? payments : List.of());
            model.addAttribute("currentPage", pageResponse.getNumber());
            model.addAttribute("pageSize", pageResponse.getSize());
            model.addAttribute("totalPages", pageResponse.getTotalPages());
            model.addAttribute("totalElements", pageResponse.getTotalElements());
        } catch (Exception e) {
            logger.error("Error fetching payments: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to fetch payments: " + e.getMessage());
            model.addAttribute("payments", List.of());
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 10);
            model.addAttribute("totalPages", 1);
            model.addAttribute("totalElements", 0);
        }
        return "payment";
    }

    @GetMapping("/payments/add")
    public String getAddPaymentForm(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        model.addAttribute("paymentDTO", new PaymentDTO());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        return "add-payment";
    }

    @PostMapping("/payments/add")
    public String addPayment(
            @ModelAttribute PaymentDTO paymentDTO,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            logger.info("Posting payment: {}", paymentDTO);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PaymentDTO> request = new HttpEntity<>(paymentDTO, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/add",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                logger.info("Payment added successfully, redirecting to next page");
                int totalPages = getTotalPages(size);
                int nextPage = Math.min(page + 1, totalPages - 1);
                redirectAttributes.addFlashAttribute("success", "Your data was added successfully");
                return "redirect:/payments?page=" + nextPage + "&size=" + size;
            } else {
                String errorMessage = response.getBody() != null ? response.getBody() : "No response";
                logger.warn("Failed to add payment: {}", errorMessage);
                model.addAttribute("error", "Failed to add payment: " + errorMessage);
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage;
            try {
                Map<String, String> errorResponse = objectMapper.readValue(
                        e.getResponseBodyAsString(),
                        Map.class
                );
                errorMessage = errorResponse.getOrDefault("message",
                        errorResponse.getOrDefault("error", e.getMessage()));
            } catch (Exception parseEx) {
                errorMessage = e.getResponseBodyAsString() != null &&
                        !e.getResponseBodyAsString().isEmpty() ?
                        e.getResponseBodyAsString() : e.getMessage();
            }
            logger.error("HTTP error adding payment: {}", errorMessage, e);
            model.addAttribute("error", "Failed to add payment: " + errorMessage);
        } catch (Exception e) {
            if (e.getCause() instanceof HttpMessageNotReadableException &&
                    e.getMessage().contains("Error while extracting response for type [class java.lang.String] and content type [application/json]")) {
                logger.info("Caught JSON parsing error but payment was added, treating as success");
                int totalPages = getTotalPages(size);
                int nextPage = Math.min(page + 1, totalPages - 1);
                redirectAttributes.addFlashAttribute("success", "Your data was added successfully");
                return "redirect:/payments?page=" + nextPage + "&size=" + size;
            }
            logger.error("Error adding payment: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to add payment: " + e.getMessage());
        }

        // On error, return to add-payment form
        model.addAttribute("paymentDTO", paymentDTO);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        return "add-payment";
    }

    private int getTotalPages(int size) {
        try {
            String url = BASE_URL + "/all?page=0&size=" + size;
            ResponseEntity<PageResponseDTO<PaymentDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PageResponseDTO<PaymentDTO>>() {}
            );
            PageResponseDTO<PaymentDTO> pageResponse = response.getBody();
            return pageResponse != null ? pageResponse.getTotalPages() : 1;
        } catch (Exception e) {
            logger.error("Error fetching total pages: {}", e.getMessage(), e);
            return 1;
        }
    }
}

