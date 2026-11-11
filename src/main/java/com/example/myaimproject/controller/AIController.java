package com.example.myaimproject.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.myaimproject.service.AIService;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> processChat(
            @RequestBody ChatRequest request,
            Authentication authentication
    ) {

        String aiResponse = aiService.processUserChat(request.prompt());

        return ResponseEntity.ok(aiResponse);
    }

    public record ChatRequest(String prompt) {
    }
}
