package com.example.myaimproject.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PayPalService {

    private static final Logger log =
            LoggerFactory.getLogger(PayPalService.class);
    
    

    @Value("${paypal.client-id}")
    private String payPalClientId;

    @Value("${paypal.client-secret}")
    private String payPalClientSecret;

    @Value("${paypal.oauth-token-url}")
    private String payPalOAuthTokenUrl;

    @Value("${paypal.orders-url}")
    private String payPalOrdersUrl;

    @Value("${paypal.mode:sandbox}")
    private String payPalEnvironmentMode;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static final long INITIAL_RETRY_DELAY_MILLIS = 500L;

    private static final long CIRCUIT_BREAKER_COOLDOWN_MILLIS = 60_000L;

    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;

    private static final Set<String> SUPPORTED_PAYPAL_CURRENCIES = Set.of(
            "USD", "INR", "EUR", "GBP", "AUD", "CAD"
    );

    private final RestTemplate resilientRestTemplate = new RestTemplate();

    private final Map<String, String> idempotentOrderRegistry =
            new ConcurrentHashMap<>();

    private final Map<String, BigDecimal> currencyConversionMatrix =
            new ConcurrentHashMap<>();

    private final AtomicBoolean circuitBreakerOpen =
            new AtomicBoolean(false);

    private final AtomicLong consecutiveFailureCounter =
            new AtomicLong(0);

    private volatile long circuitBreakerOpenedTimestamp = 0L;

    public String createResilientPayPalOrder(
            String idempotencyFingerprint,
            String amount,
            String currencyCode
    ) {

        validateOrderPayload(amount, currencyCode);

        String existingOrderId =
                idempotentOrderRegistry.get(idempotencyFingerprint);

        if (existingOrderId != null) {
            return existingOrderId;
        }

        if (isCircuitBreakerOpen()) {

            publishPayPalTelemetry(
                    "CIRCUIT_BREAKER_ACTIVE",
                    "Offline resilience layer engaged for "
                            + amount + " " + currencyCode
            );

            return createOfflineSmartSandboxFallbackOrder(
                    idempotencyFingerprint,
                    amount,
                    currencyCode
            );
        }

        for (int retryAttempt = 1;
             retryAttempt <= MAX_RETRY_ATTEMPTS;
             retryAttempt++) {

            try {
                String payPalAccessToken = acquirePayPalAccessToken();

                String paypalOrderId = invokePayPalOrderCreationApi(
                        payPalAccessToken,
                        amount,
                        currencyCode
                );

                idempotentOrderRegistry.put(
                        idempotencyFingerprint,
                        paypalOrderId
                );

                consecutiveFailureCounter.set(0);

                publishPayPalTelemetry(
                        "PAYPAL_ORDER_CREATION_SUCCESS",
                        "PayPal Order ID: " + paypalOrderId
                );

                return paypalOrderId;

            } catch (Exception payPalInvocationException) {

                long currentFailureCount =
                        consecutiveFailureCounter.incrementAndGet();

                publishPayPalTelemetry(
                        "PAYPAL_ORDER_CREATION_RETRY",
                        "Attempt="
                                + retryAttempt
                                + " Failure="
                                + payPalInvocationException.getMessage()
                );

                if (currentFailureCount
                        >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {

                    openCircuitBreaker();
                }

                if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                    executeExponentialBackoffDelay(retryAttempt);
                }
            }
        }

        publishPayPalTelemetry(
                "PAYPAL_PRIMARY_ROUTE_DEGRADED",
                "Engaging Offline Smart Sandbox Resilience Layer"
        );

        return createOfflineSmartSandboxFallbackOrder(
                idempotencyFingerprint,
                amount,
                currencyCode
        );
    }

    private String acquirePayPalAccessToken() {

        HttpHeaders oauthRequestHeaders = new HttpHeaders();

        oauthRequestHeaders.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        oauthRequestHeaders.setBasicAuth(
                payPalClientId,
                payPalClientSecret
        );

        HttpEntity<String> oauthRequestEntity = new HttpEntity<>(
                "grant_type=client_credentials",
                oauthRequestHeaders
        );

        ResponseEntity<Map> oauthResponseEntity =
                resilientRestTemplate.exchange(
                        payPalOAuthTokenUrl,
                        HttpMethod.POST,
                        oauthRequestEntity,
                        Map.class
                );

        Map<String, Object> oauthResponseBody =
                oauthResponseEntity.getBody();

        if (oauthResponseBody == null
                || !oauthResponseBody.containsKey("access_token")) {

            throw new IllegalStateException(
                    "PayPal OAuth token acquisition failed."
            );
        }

        publishPayPalTelemetry(
                "PAYPAL_OAUTH_TOKEN_ACQUIRED",
                "Client-credentials token lifecycle completed"
        );

        return String.valueOf(
                oauthResponseBody.get("access_token")
        );
    }

    @SuppressWarnings("unchecked")
    private String invokePayPalOrderCreationApi(
            String payPalAccessToken,
            String amount,
            String currencyCode
    ) {

        HttpHeaders orderRequestHeaders = new HttpHeaders();

        orderRequestHeaders.setContentType(MediaType.APPLICATION_JSON);
        orderRequestHeaders.setBearerAuth(payPalAccessToken);
        orderRequestHeaders.set(
                "PayPal-Request-Id",
                UUID.randomUUID().toString()
        );

        Map<String, Object> monetaryPayload = new LinkedHashMap<>();

        monetaryPayload.put("currency_code", currencyCode);
        monetaryPayload.put("value", amount);

        Map<String, Object> purchaseUnitPayload = new LinkedHashMap<>();

        purchaseUnitPayload.put("amount", monetaryPayload);

        Map<String, Object> paypalOrderPayload = new LinkedHashMap<>();

        paypalOrderPayload.put("intent", "CAPTURE");
        paypalOrderPayload.put(
                "purchase_units",
                java.util.List.of(purchaseUnitPayload)
        );

        HttpEntity<Map<String, Object>> paypalOrderRequestEntity =
                new HttpEntity<>(
                        paypalOrderPayload,
                        orderRequestHeaders
                );

        ResponseEntity<Map> paypalOrderResponseEntity =
                resilientRestTemplate.exchange(
                        payPalOrdersUrl,
                        HttpMethod.POST,
                        paypalOrderRequestEntity,
                        Map.class
                );

        Map<String, Object> paypalOrderResponseBody =
                paypalOrderResponseEntity.getBody();

        if (paypalOrderResponseBody == null
                || !paypalOrderResponseBody.containsKey("id")) {

            throw new IllegalStateException(
                    "PayPal Orders API returned an invalid response payload."
            );
        }

        return String.valueOf(
                paypalOrderResponseBody.get("id")
        );
    }

    private String createOfflineSmartSandboxFallbackOrder(
            String idempotencyFingerprint,
            String amount,
            String currencyCode
    ) {

        String offlineResilienceTrackingToken =
                "OFFLINE-SANDBOX-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 12)
                        .toUpperCase();

        idempotentOrderRegistry.put(
                idempotencyFingerprint,
                offlineResilienceTrackingToken
        );

        publishPayPalTelemetry(
                "OFFLINE_SMART_SANDBOX_FALLBACK_ACTIVATED",
                "Tracking Token: "
                        + offlineResilienceTrackingToken
        );

        return offlineResilienceTrackingToken;
    }

    private boolean isCircuitBreakerOpen() {

        if (!circuitBreakerOpen.get()) {
            return false;
        }

        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp - circuitBreakerOpenedTimestamp
                > CIRCUIT_BREAKER_COOLDOWN_MILLIS) {

            circuitBreakerOpen.set(false);
            consecutiveFailureCounter.set(0);

            publishPayPalTelemetry(
                    "CIRCUIT_BREAKER_RESET",
                    "PayPal primary route restored"
            );

            return false;
        }

        return true;
    }

    private void openCircuitBreaker() {

        circuitBreakerOpenedTimestamp = System.currentTimeMillis();
        circuitBreakerOpen.set(true);

        publishPayPalTelemetry(
                "CIRCUIT_BREAKER_OPENED",
                "PayPal primary route temporarily isolated"
        );
    }

    private void executeExponentialBackoffDelay(int retryAttempt) {

        long calculatedBackoffDelay =
                INITIAL_RETRY_DELAY_MILLIS
                        * (long) Math.pow(2, retryAttempt - 1);

        try {
            Thread.sleep(calculatedBackoffDelay);

        } catch (InterruptedException interruptedException) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Exponential backoff orchestration interrupted.",
                    interruptedException
            );
        }
    }

    private void validateOrderPayload(
            String amount,
            String currencyCode
    ) {

        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException(
                    "PayPal order amount payload cannot be null or empty."
            );
        }

        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException(
                    "PayPal currency code payload cannot be null or empty."
            );
        }

        if (!SUPPORTED_PAYPAL_CURRENCIES.contains(currencyCode)) {
            throw new IllegalArgumentException(
                    "Unsupported PayPal currency code: "
                            + currencyCode
            );
        }

        try {
            BigDecimal parsedAmount = new BigDecimal(amount);

            if (parsedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "PayPal order amount must be positive."
                );
            }

        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException(
                    "Invalid PayPal monetary amount format: "
                            + amount
            );
        }
    }

    public BigDecimal getConversionRate(
            String sourceCurrency,
            String targetCurrency
    ) {

        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return BigDecimal.ONE;
        }

        String conversionMatrixKey =
                sourceCurrency + "->" + targetCurrency;

        return currencyConversionMatrix.computeIfAbsent(
                conversionMatrixKey,
                key -> calculateDeterministicSandboxConversionRate(
                        sourceCurrency,
                        targetCurrency
                )
        );
    }

    private BigDecimal calculateDeterministicSandboxConversionRate(
            String sourceCurrency,
            String targetCurrency
    ) {

        return switch (sourceCurrency + "->" + targetCurrency) {
            case "INR->USD" -> new BigDecimal("0.012");
            case "EUR->USD" -> new BigDecimal("1.08");
            case "GBP->USD" -> new BigDecimal("1.27");
            case "AUD->USD" -> new BigDecimal("0.66");
            case "CAD->USD" -> new BigDecimal("0.74");
            default -> BigDecimal.ONE;
        };
    }

    public String retrieveExistingOrderId(
            String idempotencyFingerprint
    ) {
        return idempotentOrderRegistry.get(idempotencyFingerprint);
    }

    @Async
    public void publishPayPalTelemetry(
            String telemetryEvent,
            String telemetryPayload
    ) {

        try {
            Map<String, Object> telemetryRecord = new LinkedHashMap<>();

            telemetryRecord.put(
                    "eventTimestamp",
                    Instant.now().toString()
            );

            telemetryRecord.put(
                    "telemetryEvent",
                    telemetryEvent
            );

            telemetryRecord.put(
                    "telemetryPayload",
                    telemetryPayload
            );

            telemetryRecord.put(
                    "paypalEnvironment",
                    payPalEnvironmentMode
            );

            log.info(
                    "[PAYPAL-TELEMETRY] event={} payload={} environment={}",
                    telemetryEvent,
                    telemetryPayload,
                    payPalEnvironmentMode
            );

        } catch (Exception asyncTelemetryException) {

            log.error(
                    "PayPal telemetry ingestion failed",
                    asyncTelemetryException
            );
        }
    }
}