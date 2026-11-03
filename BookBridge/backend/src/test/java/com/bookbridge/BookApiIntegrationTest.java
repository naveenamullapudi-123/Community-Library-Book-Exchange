package com.bookbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bookbridge.model.BookDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "bookbridge.mongo-tests", matches = "true")
class BookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void authenticatedBookApiPersistsRecordsAndEnforcesOwnership() throws Exception {
        String unique = UUID.randomUUID().toString();
        UserDocument owner = null;
        UserDocument otherUser = null;
        BookDocument book = null;
        try {
            JsonNode ownerAccount = register("Owner", "owner-" + unique + "@example.test");
            JsonNode otherAccount = register("Other", "other-" + unique + "@example.test");
            String ownerId = ownerAccount.path("user").path("id").asText();
            String ownerToken = ownerAccount.path("token").asText();
            String otherToken = otherAccount.path("token").asText();
            owner = userRepository.findById(ownerId).orElseThrow();
            otherUser = userRepository.findById(otherAccount.path("user").path("id").asText()).orElseThrow();

            assertNotEquals("integration-secret-123", owner.passwordHash());
            assertTrue(owner.passwordHash().startsWith("$2"));

            String bookBody = """
                    {
                      "isbn": "9780000000000",
                      "title": "Persistence Test %s",
                      "author": "Integration Author",
                      "genre": "Testing",
                      "language": "English",
                      "condition": "GOOD"
                    }
                    """.formatted(unique);
            MvcResult created = mockMvc.perform(post("/api/books")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.owner.id").value(ownerId))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andReturn();
            String bookId = objectMapper.readTree(created.getResponse().getContentAsString())
                    .path("id").asText();
            book = bookRepository.findById(bookId).orElseThrow();
            assertEquals(ownerId, book.ownerId());

            mockMvc.perform(get("/api/books/{bookId}", bookId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Persistence Test " + unique));
            mockMvc.perform(get("/api/books").param("q", unique))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(bookId));
            mockMvc.perform(get("/api/books/me/bookshelf")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books[0].id").value(bookId))
                    .andExpect(jsonPath("$.incomingRequests").isArray());

            mockMvc.perform(put("/api/books/{bookId}", bookId)
                            .header("Authorization", "Bearer " + otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookBody))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/books/{bookId}/status", bookId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"UNAVAILABLE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"));

            mockMvc.perform(delete("/api/books/{bookId}", bookId)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isNoContent());
            assertTrue(bookRepository.findById(bookId).isEmpty());
        } finally {
            if (book != null) {
                bookRepository.deleteById(book.id());
            }
            if (owner != null) {
                userRepository.deleteById(owner.id());
            }
            if (otherUser != null) {
                userRepository.deleteById(otherUser.id());
            }
        }
    }

    private JsonNode register(String displayName, String email) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "displayName", displayName,
                "email", email,
                "password", "integration-secret-123"));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
