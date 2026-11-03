package com.bookbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.bookbridge.model.BorrowRequestDocument;
import com.bookbridge.model.ExtensionRequestDocument;
import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.NotificationDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.BorrowRequestRepository;
import com.bookbridge.repository.ExtensionRequestRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.NotificationRepository;
import com.bookbridge.repository.UserRepository;
import com.bookbridge.service.DueDateReminderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "bookbridge.mongo-tests", matches = "true")
class BorrowApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BorrowRequestRepository requestRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ExtensionRequestRepository extensionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DueDateReminderService reminderService;

    @Test
    void managesRequestsLoansExtensionsReturnsAndIdempotentReminders() throws Exception {
        String unique = UUID.randomUUID().toString();
        List<String> testUserIds = new ArrayList<>();
        List<String> testBookIds = new ArrayList<>();
        List<String> testRequestIds = new ArrayList<>();
        List<String> testLoanIds = new ArrayList<>();
        List<String> testExtensionIds = new ArrayList<>();
        List<String> testNotificationIds = new ArrayList<>();
        try {
            JsonNode owner = register("Owner", "owner-" + unique + "@example.test");
            JsonNode borrower = register("Borrower", "borrower-" + unique + "@example.test");
            JsonNode outsider = register("Outsider", "outsider-" + unique + "@example.test");
            String ownerId = owner.path("user").path("id").asText();
            String borrowerId = borrower.path("user").path("id").asText();
            String outsiderId = outsider.path("user").path("id").asText();
            String ownerToken = owner.path("token").asText();
            String borrowerToken = borrower.path("token").asText();
            String outsiderToken = outsider.path("token").asText();
            testUserIds.addAll(List.of(ownerId, borrowerId, outsiderId));

            String availableBookId = createBook(ownerToken, "Borrowable " + unique, "AVAILABLE");
            String unavailableBookId = createBook(ownerToken, "Unavailable " + unique, "UNAVAILABLE");
            String rejectionBookId = createBook(ownerToken, "For rejection " + unique, "AVAILABLE");
            testBookIds.addAll(List.of(availableBookId, unavailableBookId, rejectionBookId));

            String requestId = createRequest(borrowerToken, availableBookId);
            testRequestIds.add(requestId);
            mockMvc.perform(get("/api/borrow-requests/incoming").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(requestId))
                    .andExpect(jsonPath("$[0].personalMessage").value("Please may I borrow this?"));
            mockMvc.perform(get("/api/books/me/bookshelf").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.incomingRequests[0].id").value(requestId));
            mockMvc.perform(post("/api/borrow-requests/books/{bookId}", unavailableBookId)
                            .header("Authorization", "Bearer " + borrowerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody()))
                    .andExpect(status().isConflict());
            mockMvc.perform(patch("/api/borrow-requests/{requestId}/decision", requestId)
                            .header("Authorization", "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(approveBody()))
                    .andExpect(status().isForbidden());

            LocalDate startDate = LocalDate.now().plusDays(2);
            mockMvc.perform(patch("/api/borrow-requests/{requestId}/decision", requestId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVE\",\"exchangeMethod\":\"IN_PERSON\",\"handoverDate\":\""
                                    + startDate + "\",\"handoverLocation\":\"Community library\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            LoanDocument loan = loanRepository.findAll().stream()
                    .filter(value -> requestId.equals(value.requestId()))
                    .findFirst().orElseThrow();
            testLoanIds.add(loan.id());
            assertEquals(startDate.plusDays(10), loan.dueDate());
            assertEquals("LENT_OUT", bookRepository.findById(availableBookId).orElseThrow().status().name());
            mockMvc.perform(delete("/api/books/{bookId}", availableBookId)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isConflict());
            mockMvc.perform(patch("/api/books/{bookId}/status", availableBookId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isConflict());
            mockMvc.perform(post("/api/loans/{loanId}/return", loan.id())
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/loans/borrowed").header("Authorization", "Bearer " + borrowerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dueDate").value(loan.dueDate().toString()));
            mockMvc.perform(get("/api/loans/lent").header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bookId").value(availableBookId));
            assertEquals(2, reminderService.createDueDateReminders(loan.dueDate().minusDays(3)));

            MvcResult rejectedExtensionResult = mockMvc.perform(post("/api/loans/{loanId}/extensions", loan.id())
                            .header("Authorization", "Bearer " + borrowerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"additionalDays\":5,\"message\":\"One more week, please.\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn();
            String rejectedExtensionId = objectMapper.readTree(rejectedExtensionResult.getResponse().getContentAsString())
                    .path("id").asText();
            testExtensionIds.add(rejectedExtensionId);
            mockMvc.perform(patch("/api/loans/extensions/{extensionId}/decision", rejectedExtensionId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"REJECT\",\"responseMessage\":\"Please return it on time.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            MvcResult extensionResult = mockMvc.perform(post("/api/loans/{loanId}/extensions", loan.id())
                            .header("Authorization", "Bearer " + borrowerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"additionalDays\":5,\"message\":\"Could I extend this loan?\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String extensionId = objectMapper.readTree(extensionResult.getResponse().getContentAsString())
                    .path("id").asText();
            testExtensionIds.add(extensionId);
            mockMvc.perform(get("/api/loans/extensions/incoming")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(extensionId));
            mockMvc.perform(patch("/api/loans/extensions/{extensionId}/decision", extensionId)
                            .header("Authorization", "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVE\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/loans/extensions/{extensionId}/decision", extensionId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"APPROVE\",\"responseMessage\":\"Approved.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            loan = loanRepository.findById(loan.id()).orElseThrow();
            assertEquals(startDate.plusDays(15), loan.dueDate());
            assertTrue(notificationRepository.findAll().stream()
                    .anyMatch(notification -> extensionId.equals(notification.relatedEntityId())
                            && borrowerId.equals(notification.userId())
                            && "EXTENSION_APPROVED".equals(notification.type())));

            int remindersCreated = reminderService.createDueDateReminders(loan.dueDate().minusDays(3));
            assertEquals(2, remindersCreated);
            assertEquals(0, reminderService.createDueDateReminders(loan.dueDate().minusDays(3)));
            int dueTodayReminders = reminderService.createDueDateReminders(loan.dueDate());
            assertEquals(2, dueTodayReminders);
            String reminderLoanId = loan.id();
            testNotificationIds.addAll(notificationRepository.findAll().stream()
                    .filter(notification -> reminderLoanId.equals(notification.relatedEntityId()))
                    .map(NotificationDocument::id)
                    .toList());
            mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + borrowerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].type").value("LOAN_DUE_REMINDER"));
            mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(post("/api/loans/{loanId}/return", loan.id())
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/loans/{loanId}/return", loan.id())
                            .header("Authorization", "Bearer " + borrowerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RETURNED"));
            assertEquals("AVAILABLE", bookRepository.findById(availableBookId).orElseThrow().status().name());
            mockMvc.perform(post("/api/loans/{loanId}/return", loan.id())
                            .header("Authorization", "Bearer " + borrowerToken))
                    .andExpect(status().isConflict());

            String rejectedRequestId = createRequest(borrowerToken, rejectionBookId);
            testRequestIds.add(rejectedRequestId);
            mockMvc.perform(patch("/api/borrow-requests/{requestId}/decision", rejectedRequestId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"REJECT\",\"responseMessage\":\"Not available.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.responseMessage").value("Not available."));
            BorrowRequestDocument rejected = requestRepository.findById(rejectedRequestId).orElseThrow();
            assertEquals("REJECTED", rejected.status());
            assertEquals(borrowerId, rejected.borrowerId());
            assertEquals(outsiderId, outsider.path("user").path("id").asText());
        } finally {
            testNotificationIds.forEach(notificationRepository::deleteById);
            testExtensionIds.forEach(extensionRepository::deleteById);
            testLoanIds.forEach(loanRepository::deleteById);
            testRequestIds.forEach(requestRepository::deleteById);
            testBookIds.forEach(bookRepository::deleteById);
            testUserIds.forEach(userRepository::deleteById);
        }
    }

    private JsonNode register(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", name,
                                "email", email,
                                "password", "integration-secret-123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createBook(String ownerToken, String title, String status) throws Exception {
        String request = """
                {
                  "isbn": "9780000000000",
                  "title": "%s",
                  "author": "Integration Author",
                  "condition": "GOOD",
                  "status": "%s"
                }
                """.formatted(title, status);
        MvcResult result = mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String createRequest(String borrowerToken, String bookId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/borrow-requests/books/{bookId}", bookId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String requestBody() {
        return """
                {
                  "desiredStartDate": "%s",
                  "loanDurationDays": 10,
                  "personalMessage": "Please may I borrow this?"
                }
                """.formatted(LocalDate.now().plusDays(2));
    }

    private String approveBody() {
        return """
                {
                  "decision": "APPROVE",
                  "exchangeMethod": "POSTAL_OR_COURIER",
                  "handoverDate": "%s",
                  "handoverLocation": "Post office"
                }
                """.formatted(LocalDate.now().plusDays(2));
    }
}
