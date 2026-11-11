package com.example.myaimproject.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.myaimproject.service.AIService;

@RestController
@RequestMapping("/api/ai-paypal")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final AIService aiService;

    public ChatController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping(
            value = "/chat",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> chatWithAI(
            @RequestParam(name = "message") String message
    ) {
        String normalizedMessage = message.trim();

        if (normalizedMessage.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body("Please enter a non-empty message.");
        }

        if (normalizedMessage.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            "Message must not exceed "
                                    + MAX_MESSAGE_LENGTH
                                    + " characters."
                    );
        }

        String response = aiService.processUserChat(normalizedMessage);

        return ResponseEntity
                .ok()
                .cacheControl(
                        org.springframework.http.CacheControl.noStore()
                )
                .body(response);
    }
}