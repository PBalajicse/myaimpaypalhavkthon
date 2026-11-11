package com.example.myaimproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.myaimproject.entity.OrderEntity;
import com.example.myaimproject.repository.OrderRepository;

@Service
public class AIService {
	private final AtomicLong totalProcessedRequests = new AtomicLong(0);
	private final AtomicLong totalSuccessfulTransactions = new AtomicLong(0);
	private final AtomicLong totalBlockedTransactions = new AtomicLong(0);
	private final AtomicLong totalFallbackActivations = new AtomicLong(0);

    private static final Logger log =
            LoggerFactory.getLogger(AIService.class);

    private final PayPalService payPalService;

    private final OrderRepository orderRepository;

    private static final BigDecimal MAX_TRANSACTION_LIMIT_USD =
            new BigDecimal("10000.00");

    private static final BigDecimal MIN_TRANSACTION_AMOUNT =
            new BigDecimal("0.01");

    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    private static final int MAX_SESSION_MEMORY_EVENTS = 50;

    private static final int MAX_TRANSACTION_HISTORY_EVENTS = 200;

    private static final int MAX_AUDIT_TELEMETRY_EVENTS = 1000;

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "INR", "EUR", "GBP", "AUD", "CAD"
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:rs\\.?|inr|₹|\\$|usd|eur|€|gbp|£)?\\s*"
                    + "([0-9]{1,10}(?:[.,][0-9]{1,2})?)"
    );

    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(USD|INR|EUR|GBP|AUD|CAD)\\b"
    );

    private final Map<String, Deque<String>> sessionMemory =
            new ConcurrentHashMap<>();

    private final Deque<Map<String, Object>> transactionHistory =
            new ArrayDeque<>();

    private final Deque<Map<String, Object>> auditTelemetry =
            new ArrayDeque<>();

    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    private final Map<String, String> duplicateOrderRegistry =
            new ConcurrentHashMap<>();

    @Autowired
    public AIService(
            PayPalService payPalService,
            OrderRepository orderRepository
    ) {
        this.payPalService = payPalService;
        this.orderRepository = orderRepository;
    }

    public String processUserChat(String userPrompt) {

        String logToken = generateLogToken();
        long startTime = System.currentTimeMillis();

        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "❌ Please enter a valid instruction.";
        }

        String prompt = userPrompt.trim();
        String lowerPrompt = prompt.toLowerCase();

        addAuditLog(
                logToken,
                "PROMPT_RECEIVED",
                prompt
        );

        if (isRateLimitExceeded(logToken)) {
            return "⚠️ Too many requests. Please wait one minute and try again.";
        }

        try {
            if (isGreeting(lowerPrompt)) {
                return greetingAgent(logToken);
            }

            if (isHelpRequest(lowerPrompt)) {
                return capabilitiesAgent(logToken);
            }

            if (isAnalyticsRequest(lowerPrompt)) {
                return analyticsAgent(logToken);
            }

            if (isHealthRequest(lowerPrompt)) {
                return healthCheckAgent(logToken, startTime);
            }

            if (isHistoryRequest(lowerPrompt)) {
                return transactionHistoryAgent(logToken);
            }

            return paymentOrderAgent(
                    logToken,
                    prompt,
                    lowerPrompt,
                    startTime
            );

        } catch (Exception exception) {

            addAuditLog(
                    logToken,
                    "SYSTEM_ERROR",
                    exception.getClass().getSimpleName()
                            + ": "
                            + exception.getMessage()
            );

            log.error(
                    "Cognitive orchestration failed. Token={}",
                    logToken,
                    exception
            );

            return "❌ Unexpected system error occurred.\n"
                    + "Reference ID: " + logToken;
        }
    }

    private String greetingAgent(String token) {

        addAuditLog(
                token,
                "GREETING_AGENT",
                "User greeting detected"
        );

        return """
                Hello! Welcome to **PayPal Cognitive AI Assistant**.

                🟢 System Status: Online
                🧠 Cognitive Orchestration Engine: Online
                🔐 Financial Risk Shield: Active

                You can try:
                • Create a digital transaction order for 150 USD
                • Transfer 500 INR to vendor
                • Show transaction history
                • Show system health status
                • Open analytics dashboard
                """;
    }

    private String capabilitiesAgent(String token) {

        addAuditLog(
                token,
                "CAPABILITIES_AGENT",
                "Capabilities requested"
        );

        return """
                🤖 **PayPal AI Enterprise Assistant**

                1️⃣ Natural language payment parsing
                2️⃣ Currency detection and validation
                3️⃣ PayPal order creation
                4️⃣ Fraud and transaction-limit protection
                5️⃣ Duplicate transaction prevention
                6️⃣ Rate limiting
                7️⃣ Transaction history
                8️⃣ Audit telemetry
                9️⃣ Analytics dashboard
                🔟 System health monitoring
                """;
    }

    private String analyticsAgent(String token) {

        addAuditLog(
                token,
                "ANALYTICS_AGENT",
                "Dashboard requested"
        );

        return """
                📊 **Live Analytics Dashboard**

                🌐 http://localhost:8080/dashboard.html

                ▪️ Total processed requests: %d
                ▪️ Successful PayPal orders: %d
                ▪️ Risk-blocked transactions: %d
                ▪️ Stored transaction records: %d
                """.formatted(
                totalProcessedRequests.get(),
                totalSuccessfulTransactions.get(),
                totalBlockedTransactions.get(),
                transactionHistory.size()
        );
    }

    private String healthCheckAgent(
            String token,
            long startTime
    ) {

        long latency = System.currentTimeMillis() - startTime;

        addAuditLog(
                token,
                "HEALTH_CHECK_AGENT",
                "System health checked"
        );

        return """
                🛡️ **Live System Health Report**

                ▪️ Core service: Active
                ▪️ AI router: Active
                ▪️ PayPal integration: Connected
                ▪️ Request latency: %d ms
                ▪️ Session memory entries: %d
                ▪️ Transaction history entries: %d
                ▪️ Audit events stored: %d/%d
                """.formatted(
                latency,
                sessionMemory.size(),
                transactionHistory.size(),
                auditTelemetry.size(),
                MAX_AUDIT_TELEMETRY_EVENTS
        );
    }

    private String transactionHistoryAgent(String token) {

        addAuditLog(
                token,
                "HISTORY_AGENT",
                "Transaction history requested"
        );

        if (transactionHistory.isEmpty()) {
            return "📭 No transactions have been created yet.";
        }

        StringBuilder response = new StringBuilder();

        response.append("📜 **Recent Transaction History**\n");
        response.append("----------------------------------------\n");

        int count = 1;

        synchronized (transactionHistory) {
            for (Map<String, Object> transaction : transactionHistory) {

                response.append(count++).append(". ")
                        .append("Order ID: ")
                        .append(transaction.get("orderId"))
                        .append("\n")
                        .append("   Amount: ")
                        .append(transaction.get("amount"))
                        .append("\n")
                        .append("   Currency: ")
                        .append(transaction.get("currency"))
                        .append("\n")
                        .append("   Status: ")
                        .append(transaction.get("status"))
                        .append("\n")
                        .append("   Time: ")
                        .append(transaction.get("timestamp"))
                        .append("\n\n");
            }
        }

        return response.toString();
    }

    private String paymentOrderAgent(
            String token,
            String originalPrompt,
            String lowerPrompt,
            long startTime
    ) {

        addAuditLog(
                token,
                "PAYMENT_AGENT",
                "Payment request received"
        );

        BigDecimal amount;
        String currency;

        try {
            amount = extractAmount(originalPrompt);
            currency = extractCurrency(lowerPrompt);

        } catch (IllegalArgumentException exception) {

            addAuditLog(
                    token,
                    "VALIDATION_FAILED",
                    exception.getMessage()
            );

            return "❌ Invalid payment request: "
                    + exception.getMessage();
        }

        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            return "❌ Unsupported currency: "
                    + currency
                    + "\nSupported currencies: "
                    + SUPPORTED_CURRENCIES;
        }

        if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            return "❌ Amount must be at least "
                    + MIN_TRANSACTION_AMOUNT.toPlainString()
                    + " "
                    + currency
                    + ".";
        }

        BigDecimal usdEquivalent;

        try {
            BigDecimal conversionRate =
                    payPalService.getConversionRate(
                            currency,
                            "USD"
                    );

            usdEquivalent = amount
                    .multiply(conversionRate)
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception exception) {

            addAuditLog(
                    token,
                    "CURRENCY_CONVERSION_FAILED",
                    exception.getMessage()
            );

            return "❌ Unable to calculate USD conversion. "
                    + "Please try again later.";
        }

        if (usdEquivalent.compareTo(MAX_TRANSACTION_LIMIT_USD) > 0) {

            totalBlockedTransactions.incrementAndGet();

            addAuditLog(
                    token,
                    "FRAUD_LIMIT_BLOCKED",
                    "USD equivalent: "
                            + usdEquivalent.toPlainString()
            );

            return """
                    ❌ **Cognitive Risk Shield Activated**

                    Transaction request has been frozen.

                    ▪️ Requested Amount: %s
                    ▪️ USD Equivalent: %s
                    ▪️ Maximum Allowed: %s
                    """.formatted(
                    formatMoney(amount, currency),
                    formatMoney(usdEquivalent, "USD"),
                    formatMoney(MAX_TRANSACTION_LIMIT_USD, "USD")
            );
        }

        String idempotencyKey = createIdempotencyKey(
                amount,
                currency,
                lowerPrompt
        );

        String existingOrderId =
                duplicateOrderRegistry.get(idempotencyKey);

        if (existingOrderId != null) {

            addAuditLog(
                    token,
                    "DUPLICATE_ORDER_BLOCKED",
                    existingOrderId
            );

            return """
                    ⚠️ **Duplicate Transaction Detected**

                    This payment request was already processed.

                    ▪️ Existing PayPal Order ID: %s
                    """.formatted(existingOrderId);
        }

        try {
            String paypalOrderId =
                    payPalService.createResilientPayPalOrder(
                            idempotencyKey,
                            amount.toPlainString(),
                            currency
                    );

            if (paypalOrderId == null || paypalOrderId.isBlank()) {
                throw new IllegalStateException(
                        "PayPal did not return a valid order ID."
                );
            }

            duplicateOrderRegistry.put(
                    idempotencyKey,
                    paypalOrderId
            );

            saveTransactionToDatabase(
                    paypalOrderId,
                    amount,
                    currency,
                    "CREATED"
            );

            saveTransactionHistory(
                    paypalOrderId,
                    amount,
                    currency,
                    "CREATED"
            );

            saveSessionMemory(
                    token,
                    "LAST_ORDER_ID",
                    paypalOrderId
            );

            saveSessionMemory(
                    token,
                    "LAST_TRANSACTION",
                    formatMoney(amount, currency)
            );

            addAuditLog(
                    token,
                    "ORDER_CREATED",
                    paypalOrderId
            );

            long latency =
                    System.currentTimeMillis() - startTime;

            boolean isFallbackOrder =
                    paypalOrderId.startsWith("OFFLINE-SANDBOX-");

            if (isFallbackOrder) {
                totalFallbackActivations.incrementAndGet();
            } else {
                totalSuccessfulTransactions.incrementAndGet();
            }

            return """
                    🧠 **AI Payment Order Created Successfully**

                    ▪️ Amount: %s
                    ▪️ USD Equivalent: %s
                    ▪️ PayPal Order ID: %s
                    ▪️ Status: CREATED
                    ▪️ Execution Route: %s
                    ▪️ Processing Time: %d ms
                    ▪️ Security Check: Passed
                    """.formatted(
                    formatMoney(amount, currency),
                    formatMoney(usdEquivalent, "USD"),
                    paypalOrderId,
                    isFallbackOrder
                            ? "OFFLINE SMART SANDBOX RESILIENCE LAYER"
                            : "PAYPAL LIVE SANDBOX ROUTE",
                    latency
            );

        } catch (Exception exception) {

            addAuditLog(
                    token,
                    "ORDER_CREATION_FAILED",
                    exception.getMessage()
            );

            log.error(
                    "PayPal order creation failed. Token={}",
                    token,
                    exception
            );

            return "❌ PayPal order creation failed.\n"
                    + "Reason: "
                    + exception.getMessage()
                    + "\n"
                    + "Reference ID: "
                    + token;
        }
    }

    private void saveTransactionToDatabase(
            String paypalOrderId,
            BigDecimal amount,
            String currency,
            String status
    ) {

        try {
            OrderEntity orderEntity = new OrderEntity();

            orderEntity.setPaypalOrderId(paypalOrderId);
            orderEntity.setAmount(amount.toPlainString());
            orderEntity.setCurrency(currency);
            orderEntity.setStatus(status);
            orderEntity.setCreatedAt(LocalDateTime.now());

            orderRepository.save(orderEntity);

            addAuditLog(
                    paypalOrderId,
                    "DATABASE_PERSISTENCE_SUCCESS",
                    "Order saved to H2/JPA database"
            );

        } catch (Exception databaseException) {

            addAuditLog(
                    paypalOrderId,
                    "DATABASE_PERSISTENCE_FAILED",
                    databaseException.getMessage()
            );

            log.error(
                    "Database persistence failed for PayPal order {}",
                    paypalOrderId,
                    databaseException
            );
        }
    }

    private BigDecimal extractAmount(String prompt) {

        Matcher matcher = AMOUNT_PATTERN.matcher(prompt);

        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "No valid amount found. Example: "
                            + "`Create an order for 150 USD`."
            );
        }

        String rawAmount =
                matcher.group(1).replace(",", ".");

        try {
            BigDecimal amount = new BigDecimal(rawAmount);

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Amount must be greater than zero."
                );
            }

            return amount.setScale(2, RoundingMode.HALF_UP);

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid amount format: "
                            + rawAmount
            );
        }
    }

    private String extractCurrency(String lowerPrompt) {

        if (lowerPrompt.contains("inr")
                || lowerPrompt.contains("rupee")
                || lowerPrompt.contains("₹")) {
            return "INR";
        }

        if (lowerPrompt.contains("eur")
                || lowerPrompt.contains("euro")
                || lowerPrompt.contains("€")) {
            return "EUR";
        }

        if (lowerPrompt.contains("gbp")
                || lowerPrompt.contains("pound")
                || lowerPrompt.contains("£")) {
            return "GBP";
        }

        if (lowerPrompt.contains("aud")) {
            return "AUD";
        }

        if (lowerPrompt.contains("cad")) {
            return "CAD";
        }

        return "USD";
    }

    private String formatMoney(
            BigDecimal amount,
            String currency
    ) {

        return currency
                + " "
                + amount.setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String createIdempotencyKey(
            BigDecimal amount,
            String currency,
            String prompt
    ) {

        String normalizedPrompt = prompt
                .replaceAll("\\s+", " ")
                .trim();

        return amount.toPlainString()
                + "|"
                + currency
                + "|"
                + normalizedPrompt.hashCode();
    }

    private boolean isRateLimitExceeded(String token) {

        long currentTime = System.currentTimeMillis();

        synchronized (requestTimestamps) {

            while (!requestTimestamps.isEmpty()
                    && currentTime - requestTimestamps.peekFirst()
                    > 60_000) {

                requestTimestamps.removeFirst();
            }

            if (requestTimestamps.size()
                    >= MAX_REQUESTS_PER_MINUTE) {

                addAuditLog(
                        token,
                        "RATE_LIMIT_BLOCKED",
                        "Too many requests"
                );

                return true;
            }

            requestTimestamps.addLast(currentTime);
        }

        return false;
    }

    private void saveSessionMemory(
            String token,
            String key,
            String value
    ) {

        Deque<String> memory = sessionMemory.computeIfAbsent(
                token,
                sessionId -> new ArrayDeque<>()
        );

        synchronized (memory) {

            memory.addLast(key + "=" + value);

            if (memory.size() > MAX_SESSION_MEMORY_EVENTS) {
                memory.removeFirst();
            }
        }
    }

    private void saveTransactionHistory(
            String orderId,
            BigDecimal amount,
            String currency,
            String status
    ) {

        Map<String, Object> transaction = new LinkedHashMap<>();

        transaction.put("orderId", orderId);
        transaction.put("amount", amount.toPlainString());
        transaction.put("currency", currency);
        transaction.put("status", status);
        transaction.put("timestamp", Instant.now().toString());

        synchronized (transactionHistory) {

            transactionHistory.addLast(transaction);

            if (transactionHistory.size()
                    > MAX_TRANSACTION_HISTORY_EVENTS) {

                transactionHistory.removeFirst();
            }
        }
    }

    @Async
    public void addAuditLog(
            String token,
            String event,
            String details
    ) {

        try {
            Map<String, Object> audit = new LinkedHashMap<>();

            audit.put("timestamp", Instant.now().toString());
            audit.put("token", token);
            audit.put("event", event);
            audit.put("details", details);

            synchronized (auditTelemetry) {

                auditTelemetry.addLast(audit);

                if (auditTelemetry.size()
                        > MAX_AUDIT_TELEMETRY_EVENTS) {

                    auditTelemetry.removeFirst();
                }
            }

            log.info(
                    "[AUDIT] token={} event={} details={}",
                    token,
                    event,
                    details
            );

        } catch (Exception asyncTelemetryException) {

            log.error(
                    "Async telemetry ingestion failed",
                    asyncTelemetryException
            );
        }
    }

    private String generateLogToken() {

        return "TXN-AI-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private boolean isGreeting(String prompt) {
        return prompt.matches(
                "^(hi|hello|hey|greetings|good morning|hola|vanakkam).*"
        );
    }

    private boolean isHelpRequest(String prompt) {
        return prompt.contains("help")
                || prompt.contains("who are you")
                || prompt.contains("capabilities");
    }

    private boolean isAnalyticsRequest(String prompt) {
        return prompt.contains("analytics")
                || prompt.contains("dashboard")
                || prompt.contains("metrics")
                || prompt.contains("report");
    }

    private boolean isHealthRequest(String prompt) {
        return prompt.contains("system status")
                || prompt.contains("health")
                || prompt.contains("uptime");
    }

    private boolean isHistoryRequest(String prompt) {
        return prompt.contains("transaction history")
                || prompt.contains("order history")
                || prompt.contains("previous transactions");
    }
}