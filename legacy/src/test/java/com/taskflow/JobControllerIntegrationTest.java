package com.taskflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.api.dto.CreateJobRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("JobController MockMvc REST Integration Tests")
class JobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/jobs should create job (201) and replaying same key returns cached (200)")
    void testCreateJobAndIdempotentReplay() throws Exception {
        String testKey = "idemp-mvc-" + UUID.randomUUID();
        CreateJobRequest request = CreateJobRequest.builder()
                .type("PDF_GENERATION")
                .payload("{\"docId\": 9942}")
                .priority(6)
                .maxRetries(3)
                .build();

        // First Call: Creates job -> HTTP 201 Created
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", testKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.idempotencyKey", is(testKey)))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.cachedIdempotentResponse", is(false)));

        // Second Call: Same Key -> HTTP 200 OK with cached response
        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", testKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.cachedIdempotentResponse", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/metrics returns system metrics")
    void testGetMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").isNumber())
                .andExpect(jsonPath("$.successRate").isNumber());
    }
}
