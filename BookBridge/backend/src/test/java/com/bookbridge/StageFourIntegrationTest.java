package com.bookbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.bookbridge.model.ForumPostDocument;
import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.MessageDocument;
import com.bookbridge.model.NotificationDocument;
import com.bookbridge.model.ReviewDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.BorrowRequestRepository;
import com.bookbridge.repository.ForumPostRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.MessageRepository;
import com.bookbridge.repository.NotificationRepository;
import com.bookbridge.repository.ReviewRepository;
import com.bookbridge.repository.UserRepository;
import com.bookbridge.repository.WishlistRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "bookbridge.mongo-tests", matches = "true")
class StageFourIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BorrowRequestRepository borrowRequestRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ForumPostRepository forumPostRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void wishlistReviewsProfilesForumMessagesAndNotificationsArePrivateAndPersistent() throws Exception {
        String unique = UUID.randomUUID().toString();
        List<String> userIds = new ArrayList<>();
        List<String> bookIds = new ArrayList<>();
        List<String> requestIds = new ArrayList<>();
        List<String> loanIds = new ArrayList<>();
        List<String> notificationIds = new ArrayList<>();
        List<String> postIds = new ArrayList<>();
        List<String> messageIds = new ArrayList<>();
        try {
            JsonNode owner = register("Owner", "stage4-owner-" + unique + "@example.test");
            JsonNode borrower = register("Borrower", "stage4-borrower-" + unique + "@example.test");
            JsonNode outsider = register("Outsider", "stage4-outsider-" + unique + "@example.test");
            String ownerId = owner.path("user").path("id").asText();
            String borrowerId = borrower.path("user").path("id").asText();
            String outsiderId = outsider.path("user").path("id").asText();
            String ownerToken = owner.path("token").asText();
            String borrowerToken = borrower.path("token").asText();
            String outsiderToken = outsider.path("token").asText();
            userIds.addAll(List.of(ownerId, borrowerId, outsiderId));

            String wishedBookId = createBook(ownerToken, "Wishlist Book " + unique, "UNAVAILABLE");
            bookIds.add(wishedBookId);
            mockMvc.perform(put("/api/wishlist/{bookId}", wishedBookId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books[0].id").value(wishedBookId));
            mockMvc.perform(put("/api/wishlist/{bookId}", wishedBookId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books.length()").value(1));
            mockMvc.perform(get("/api/wishlist").header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books").isEmpty());
            mockMvc.perform(patch("/api/books/{bookId}/status", wishedBookId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/books/{bookId}/status", wishedBookId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"UNAVAILABLE\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/books/{bookId}/status", wishedBookId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/books/{bookId}/status", wishedBookId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"AVAILABLE\"}"))
                    .andExpect(status().isOk());
            List<NotificationDocument> wishlistNotifications = notificationRepository.findAll().stream()
                    .filter(notification -> wishedBookId.equals(notification.relatedEntityId())
                            && borrowerId.equals(notification.userId())
                            && "WISHLIST_AVAILABLE".equals(notification.type()))
                    .toList();
            assertEquals(2, wishlistNotifications.size());
            notificationIds.add(wishlistNotifications.get(0).id());
            mockMvc.perform(delete("/api/wishlist/{bookId}", wishedBookId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.books").isEmpty());

            String loanBookId = createBook(ownerToken, "Review Book " + unique, "AVAILABLE");
            bookIds.add(loanBookId);
            MvcResult requestResult = mockMvc.perform(post("/api/borrow-requests/books/{bookId}", loanBookId)
                            .header("Authorization", bearer(borrowerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody()))
                    .andExpect(status().isCreated())
                    .andReturn();
            String requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                    .path("id").asText();
            requestIds.add(requestId);
            LocalDate startDate = LocalDate.now().plusDays(2);
            mockMvc.perform(patch("/api/borrow-requests/{requestId}/decision", requestId)
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(approveBody(startDate)))
                    .andExpect(status().isOk());
            LoanDocument loan = loanRepository.findAll().stream()
                    .filter(value -> requestId.equals(value.requestId()))
                    .findFirst().orElseThrow();
            loanIds.add(loan.id());
            mockMvc.perform(post("/api/loans/{loanId}/return", loan.id())
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/loans/returned")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(loan.id()));
            mockMvc.perform(get("/api/loans/returned")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(loan.id()));
            mockMvc.perform(get("/api/loans/borrowed")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            mockMvc.perform(get("/api/loans/lent")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            mockMvc.perform(get("/api/loans/returned")
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            mockMvc.perform(get("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            mockMvc.perform(get("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isForbidden());

            String reviewBody = """
                    {"reliability":5,"communication":4,"careOfBooks":3,"comment":"A pleasant exchange."}
                    """;
            mockMvc.perform(post("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(borrowerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reviewBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.averageRating").value(4.0));
            mockMvc.perform(get("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].reviewerId").value(borrowerId));
            mockMvc.perform(post("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(borrowerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reviewBody))
                    .andExpect(status().isConflict());
            mockMvc.perform(post("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(outsiderToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reviewBody))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users/{userId}/profile", ownerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.id").value(ownerId))
                    .andExpect(jsonPath("$.trustScore.score").value(4.0))
                    .andExpect(jsonPath("$.trustScore.reviewCount").value(1))
                    .andExpect(jsonPath("$.listedBooks.length()").value(2))
                    .andExpect(jsonPath("$.lendingHistory.length()").value(1));
            MvcResult profileResult = mockMvc.perform(get("/api/users/{userId}/profile", ownerId))
                    .andReturn();
            String profileJson = profileResult.getResponse().getContentAsString();
            assertFalse(profileJson.contains("passwordHash"));
            assertFalse(profileJson.contains("reader@example.test"));
            mockMvc.perform(get("/api/users/{userId}/profile", outsiderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trustScore.score").value(0.0))
                    .andExpect(jsonPath("$.trustScore.reviewCount").value(0));
            mockMvc.perform(post("/api/loans/{loanId}/reviews", loan.id())
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reliability":2,"communication":3,"careOfBooks":4,"comment":"Returned in good time."}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.averageRating").value(3.0));
            MvcResult reviewsResult = mockMvc.perform(get("/api/loans/{loanId}/reviews", loan.id())
                                            .header("Authorization", bearer(ownerToken)))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.length()").value(2))
                                    .andReturn();
            String reviewsJson = reviewsResult.getResponse().getContentAsString();
            assertTrue(reviewsJson.contains(borrowerId));
            assertTrue(reviewsJson.contains(ownerId));
            mockMvc.perform(get("/api/users/{userId}/profile", borrowerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trustScore.score").value(3.0))
                    .andExpect(jsonPath("$.trustScore.reviewCount").value(1))
                    .andExpect(jsonPath("$.borrowingHistory.length()").value(1));

            MvcResult postResult = mockMvc.perform(post("/api/forum/posts")
                            .header("Authorization", bearer(borrowerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"RECOMMENDATION","title":"A good read","content":"Try this book."}
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn();
            String postId = objectMapper.readTree(postResult.getResponse().getContentAsString()).path("id").asText();
            postIds.add(postId);
            mockMvc.perform(get("/api/forum/posts/{postId}", postId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorId").value(borrowerId));
            mockMvc.perform(get("/api/forum/posts")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '" + postId + "')]").isNotEmpty());
            mockMvc.perform(put("/api/forum/posts/{postId}", postId)
                            .header("Authorization", bearer(outsiderToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"EVENT","title":"Changed","content":"No permission."}
                                    """))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/forum/posts/{postId}", postId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/forum/posts/{postId}", postId)
                            .header("Authorization", bearer(borrowerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"READING_LIST","title":"Spring reading list","content":"A short list."}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Spring reading list"));
            mockMvc.perform(delete("/api/forum/posts/{postId}", postId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isNoContent());

            MvcResult messageResult = mockMvc.perform(post("/api/messages")
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "recipientId", borrowerId,
                                    "content", "The book is ready for pickup."))))
                    .andExpect(status().isCreated())
                    .andReturn();
            String messageId = objectMapper.readTree(messageResult.getResponse().getContentAsString())
                    .path("id").asText();
            messageIds.add(messageId);
            mockMvc.perform(get("/api/messages/with/{otherUserId}", borrowerId)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].content").value("The book is ready for pickup."));
            mockMvc.perform(get("/api/messages/with/{otherUserId}", ownerId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].readAt").isNotEmpty());
            mockMvc.perform(get("/api/messages/with/{otherUserId}", ownerId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            assertTrue(messageRepository.findById(messageId).orElseThrow().readAt() != null);

            mockMvc.perform(get("/api/notifications/unread")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isNotEmpty());
            mockMvc.perform(get("/api/notifications/unread")
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            String wishlistNotificationId = wishlistNotifications.get(0).id();
            mockMvc.perform(patch("/api/notifications/{notificationId}/read", wishlistNotificationId)
                            .header("Authorization", bearer(outsiderToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/notifications/{notificationId}/read", wishlistNotificationId)
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.read").value(true));
            mockMvc.perform(patch("/api/notifications/read-all")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/notifications/unread")
                            .header("Authorization", bearer(borrowerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            notificationIds.addAll(notificationRepository.findAll().stream()
                    .filter(notification -> userIds.contains(notification.userId()))
                    .map(NotificationDocument::id)
                    .toList());
        } finally {
            messageIds.forEach(messageRepository::deleteById);
            postIds.forEach(forumPostRepository::deleteById);
            reviewRepository.findAll().stream()
                    .filter(review -> loanIds.contains(review.loanId()))
                    .map(ReviewDocument::id)
                    .toList()
                    .forEach(reviewRepository::deleteById);
            notificationRepository.findAll().stream()
                    .filter(notification -> userIds.contains(notification.userId()))
                    .map(NotificationDocument::id)
                    .toList()
                    .forEach(notificationRepository::deleteById);
            loanIds.forEach(loanRepository::deleteById);
            requestIds.forEach(borrowRequestRepository::deleteById);
            bookIds.forEach(bookRepository::deleteById);
            wishlistRepository.findAll().stream()
                    .filter(wishlist -> userIds.contains(wishlist.userId()))
                    .map(com.bookbridge.model.WishlistDocument::id)
                    .toList()
                    .forEach(wishlistRepository::deleteById);
            userIds.forEach(userRepository::deleteById);
        }
    }

    private JsonNode register(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", name,
                                "email", email,
                                "password", "stage4-secret-123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createBook(String ownerToken, String title, String statusValue) throws Exception {
        String body = """
                {
                  "isbn": "9780000000000",
                  "title": "%s",
                  "author": "Stage Four Author",
                  "condition": "GOOD",
                  "status": "%s"
                }
                """.formatted(title, statusValue);
        MvcResult result = mockMvc.perform(post("/api/books")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String requestBody() {
        return """
                {"desiredStartDate":"%s","loanDurationDays":7,"personalMessage":"Please may I borrow this?"}
                """.formatted(LocalDate.now().plusDays(2));
    }

    private String approveBody(LocalDate startDate) {
        return """
                {"decision":"APPROVE","exchangeMethod":"IN_PERSON","handoverDate":"%s","handoverLocation":"Library"}
                """.formatted(startDate);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
