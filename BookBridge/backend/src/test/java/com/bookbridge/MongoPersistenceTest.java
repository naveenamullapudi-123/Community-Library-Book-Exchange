package com.bookbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.BookStatus;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.UserRepository;

@SpringBootTest
@EnabledIfSystemProperty(named = "bookbridge.mongo-tests", matches = "true")
class MongoPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void savesAndReadsUsersAndBooksFromMongoThenRemovesTheTestRecords() {
        String unique = UUID.randomUUID().toString();
        UserDocument savedUser = null;
        BookDocument savedBook = null;
        try {
            savedUser = userRepository.save(new UserDocument(
                    null, "Persistence Test", "persistence-" + unique + "@example.test",
                    "test-hash", null, Instant.now()));
            savedBook = bookRepository.save(new BookDocument(
                    null, "978" + unique.replace("-", "").substring(0, 10),
                    "Mongo persistence test", "Test Author", "Testing", "en", null, null, null,
                    BookCondition.GOOD, BookStatus.AVAILABLE, savedUser.id(), 14, null, null,
                    Instant.now(), Instant.now()));

            assertTrue(userRepository.findById(savedUser.id()).isPresent());
            BookDocument reloaded = bookRepository.findById(savedBook.id()).orElseThrow();
            assertEquals("Mongo persistence test", reloaded.title());
            assertEquals(savedUser.id(), reloaded.ownerId());
        } finally {
            if (savedBook != null) {
                bookRepository.deleteById(savedBook.id());
            }
            if (savedUser != null) {
                userRepository.deleteById(savedUser.id());
            }
        }
    }
}
