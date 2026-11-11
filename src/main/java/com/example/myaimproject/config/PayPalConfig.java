package com.example.myaimproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PayPalConfig {

    public static final String BASE_URL = "https://paypal.com"; // For Testing
    public static final String CLIENT_ID = "YOUR_PAYPAL_SANDBOX_CLIENT_ID";
    public static final String CLIENT_SECRET = "YOUR_PAYPAL_SANDBOX_SECRET";

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
