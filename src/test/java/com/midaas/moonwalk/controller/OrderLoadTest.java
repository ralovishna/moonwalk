package com.midaas.moonwalk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderLoadTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();

    private final List<String> dishes = List.of(
            "Galactic Burger", "Lunar Pizza", "Meteor Pasta", "Apollo Sushi", "Starship Salad"
    );

    @Test
    public void testPlace20OrdersForBackpressure() throws Exception {
        String url = "http://localhost:" + port + "/api/v1/orders";

        for (int i = 1; i <= 20; i++) {
            int prepTime = random.nextInt(10, 31);

            Map<String, Object> requestMap = Map.of(
                    "restaurantId", 1L,
                    "tableNumber", "Table " + i,
                    "dishName", dishes.get(random.nextInt(dishes.size())),
                    "basePrepTime", prepTime
            );
            String jsonBody = objectMapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.printf("Request #%02d -> SUCCESS (201) | Prep: %2ds | Body: %s%n",
                        i, prepTime, response.body());
            } else {
                System.out.printf("Request #%02d -> FAILED with status: %d%n", i, response.statusCode());
            }
        }

        Thread.sleep(200000);
    }
}