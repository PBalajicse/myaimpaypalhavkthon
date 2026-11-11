package com.example.myaimproject.service;

import org.springframework.stereotype.Service;

@Service
public class AIAgenticOrchestrator {

    // 🏆 HIGH-LEVEL AGENTIC COGNITIVE BEHAVIOR SELECTOR
    public String determineAutonomousIntent(String userPrompt) {
        String lower = userPrompt.toLowerCase().trim();
        
        System.out.println("[AGENTIC-ORCHESTRATOR] Activating Neural Prompt Template Matrix for Intent Resolution.");
        
        if (lower.contains("send") || lower.contains("pay") || lower.contains("order") || lower.contains("transfer")) {
            return "INTENT_PAYMENT_TRANSACTION";
        } else if (lower.contains("analytics") || lower.contains("dashboard") || lower.contains("chart")) {
            return "INTENT_METRICS_DASHBOARD";
        } else if (lower.contains("status") || lower.contains("health") || lower.contains("uptime")) {
            return "INTENT_SYSTEM_HEALTH";
        } else if (lower.matches("^(hi|hello|hey|vanakkam).*")) {
            return "INTENT_HANDSHAKE_GREETING";
        }
        
        return "INTENT_FALLBACK_COGNITIVE";
    }
}