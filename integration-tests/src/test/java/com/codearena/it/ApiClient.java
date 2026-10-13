package com.codearena.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ApiClient {

    private static final Set<String> OPEN = Set.of("PENDING", "RUNNING");
    private static final Duration JUDGE_TIMEOUT = Duration.ofMinutes(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    record Response(int status, JsonNode body) {
    }

    record User(long id, String token) {
    }

    User register(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        Response response = send("POST", "/api/auth/register", null, Map.of(
                "username", username, "email", username + "@example.com", "password", "password-123"));
        assertThat(response.status()).as("register %s: %s", username, response.body()).isEqualTo(201);
        return user(response.body());
    }

    User login(String username, String password) {
        Response response = send("POST", "/api/auth/login", null, Map.of("username", username, "password", password));
        assertThat(response.status()).as("login: %s", response.body()).isEqualTo(200);
        return user(response.body());
    }

    long problemId(String slug) {
        Response response = send("GET", "/api/problems/" + slug, null, null);
        assertThat(response.status()).isEqualTo(200);
        return response.body().get("id").asLong();
    }

    long submit(User user, long problemId, String language, String source, Long contestId) {
        Map<String, Object> body = contestId == null
                ? Map.of("problemId", problemId, "language", language, "sourceCode", source)
                : Map.of("problemId", problemId, "language", language, "sourceCode", source, "contestId", contestId);
        Response response = send("POST", "/api/submissions", user.token(), body);
        assertThat(response.status()).as("submit: %s", response.body()).isEqualTo(201);
        return response.body().get("id").asLong();
    }

    JsonNode awaitVerdict(User user, long submissionId) {
        Instant deadline = Instant.now().plus(JUDGE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            JsonNode submission = send("GET", "/api/submissions/" + submissionId, user.token(), null).body();
            if (!OPEN.contains(submission.get("status").asText())) {
                return submission;
            }
            pause();
        }
        throw new AssertionError("Submission " + submissionId + " was not judged within " + JUDGE_TIMEOUT);
    }

    Response send(String method, String path, String token, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = response.body().isEmpty() ? json.nullNode() : json.readTree(response.body());
            return new Response(response.statusCode(), parsed);
        } catch (IOException ex) {
            throw new IllegalStateException(method + " " + path + " failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }

    static void pause() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }

    private static User user(JsonNode auth) {
        return new User(auth.get("user").get("id").asLong(), auth.get("accessToken").asText());
    }
}
