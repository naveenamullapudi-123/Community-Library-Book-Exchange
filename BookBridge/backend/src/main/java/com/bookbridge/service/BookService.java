package com.bookbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.dto.BookshelfResponse;
import com.bookbridge.dto.IncomingRequestResponse;
import com.bookbridge.dto.PublicUserResponse;
import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.BookStatus;
import com.bookbridge.model.BorrowRequestDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.BorrowRequestRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final LoanRepository loanRepository;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    public BookService(
            BookRepository bookRepository,
            UserRepository userRepository,
            BorrowRequestRepository borrowRequestRepository,
            LoanRepository loanRepository,
            NotificationService notificationService,
            MongoTemplate mongoTemplate) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.loanRepository = loanRepository;
        this.notificationService = notificationService;
        this.mongoTemplate = mongoTemplate;
    }

    public BookResponse create(String ownerId, BookRequest request) {
        BookStatus status = request.status() == null ? BookStatus.AVAILABLE : request.status();
        Instant now = Instant.now();
        BookDocument book = new BookDocument(
                null,
                request.isbn().trim(),
                request.title().trim(),
                request.author().trim(),
                clean(request.genre()),
                clean(request.language()),
                clean(request.description()),
                clean(request.publisher()),
                clean(request.coverImageUrl()),
                requiredCondition(request.condition()),
                status,
                ownerId,
                request.loanDurationDays() == null ? 14 : request.loanDurationDays(),
                clean(request.pickupPreferences()),
                clean(request.cityOrPostalCode()),
                now,
                now);
        return toResponse(bookRepository.save(book));
    }

    public List<BookResponse> search(
            String search,
            BookStatus status,
            String language,
            BookCondition condition) {
        Criteria criteria = new Criteria();
        java.util.List<Criteria> filters = new java.util.ArrayList<>();

        if (search != null && !search.isBlank()) {
            String safeSearch = PatternSupport.escape(search.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(safeSearch, "i"),
                    Criteria.where("author").regex(safeSearch, "i"),
                    Criteria.where("genre").regex(safeSearch, "i")));
        }
        filters.add(Criteria.where("status").is(status == null ? BookStatus.AVAILABLE : status));
        if (language != null && !language.isBlank()) {
            filters.add(Criteria.where("language").regex(PatternSupport.escape(language.trim()), "i"));
        }
        if (condition != null) {
            filters.add(Criteria.where("condition").is(condition));
        }
        criteria.andOperator(filters.toArray(Criteria[]::new));

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(200);
        return mongoTemplate.find(query, BookDocument.class).stream().map(this::toResponse).toList();
    }

    public List<String> genres() {
        return mongoTemplate.query(BookDocument.class)
                .distinct("genre")
                .as(String.class)
                .matching(Criteria.where("status").is(BookStatus.AVAILABLE))
                .all().stream()
                .filter(genre -> genre != null && !genre.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public BookResponse get(String bookId) {
        return toResponse(findBook(bookId));
    }

    public BookResponse update(String userId, String bookId, BookRequest request) {
        BookDocument current = findBook(bookId);
        requireOwner(current, userId);
        if (loanRepository.existsByBookIdAndStatus(bookId, BorrowService.ACTIVE)
                && request.status() != null
                && request.status() != BookStatus.LENT_OUT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A book with an active loan must remain marked Lent Out.");
        }

        BookDocument updated = new BookDocument(
                current.id(),
                request.isbn().trim(),
                request.title().trim(),
                request.author().trim(),
                clean(request.genre()),
                clean(request.language()),
                clean(request.description()),
                clean(request.publisher()),
                clean(request.coverImageUrl()),
                requiredCondition(request.condition()),
                request.status() == null ? current.status() : request.status(),
                current.ownerId(),
                request.loanDurationDays() == null ? current.loanDurationDays() : request.loanDurationDays(),
                clean(request.pickupPreferences()),
                clean(request.cityOrPostalCode()),
                current.createdAt(),
                Instant.now());
        BookDocument saved = bookRepository.save(updated);
        if (current.status() != BookStatus.AVAILABLE && saved.status() == BookStatus.AVAILABLE) {
            notificationService.notifyWishlist(saved.isbn(), saved.title(), saved.id(), saved.updatedAt());
        }
        return toResponse(saved);
    }

    public void delete(String userId, String bookId) {
        BookDocument book = findBook(bookId);
        requireOwner(book, userId);
        if (loanRepository.existsByBookIdAndStatus(bookId, BorrowService.ACTIVE)
                || borrowRequestRepository.existsByBookIdAndStatus(bookId, BorrowService.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A book with an active loan or pending borrow request cannot be deleted.");
        }
        bookRepository.delete(book);
    }

    public BookResponse updateStatus(String userId, String bookId, BookStatus status) {
        BookDocument current = findBook(bookId);
        requireOwner(current, userId);
        if (loanRepository.existsByBookIdAndStatus(bookId, BorrowService.ACTIVE)
                && status != BookStatus.LENT_OUT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A book with an active loan must remain marked Lent Out.");
        }
        BookDocument updated = new BookDocument(
                current.id(),
                current.isbn(),
                current.title(),
                current.author(),
                current.genre(),
                current.language(),
                current.description(),
                current.publisher(),
                current.coverImageUrl(),
                current.condition(),
                status,
                current.ownerId(),
                current.loanDurationDays(),
                current.pickupPreferences(),
                current.cityOrPostalCode(),
                current.createdAt(),
                Instant.now());
        BookDocument saved = bookRepository.save(updated);
        if (current.status() != BookStatus.AVAILABLE && saved.status() == BookStatus.AVAILABLE) {
            notificationService.notifyWishlist(saved.isbn(), saved.title(), saved.id(), saved.updatedAt());
        }
        return toResponse(saved);
    }

    public BookshelfResponse bookshelf(String ownerId) {
        List<BookResponse> books = bookRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(this::toResponse).toList();
        List<IncomingRequestResponse> requests =
                borrowRequestRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, "PENDING")
                        .stream().map(this::toIncomingRequest).toList();
        return new BookshelfResponse(books, requests);
    }

    public List<BookResponse> createAll(String ownerId, List<BookRequest> requests) {
        Instant now = Instant.now();
        List<BookDocument> books = requests.stream()
                .map(request -> new BookDocument(
                        null,
                        request.isbn().trim(),
                        request.title().trim(),
                        request.author().trim(),
                        clean(request.genre()),
                        clean(request.language()),
                        clean(request.description()),
                        clean(request.publisher()),
                        clean(request.coverImageUrl()),
                        requiredCondition(request.condition()),
                        request.status() == null ? BookStatus.AVAILABLE : request.status(),
                        ownerId,
                        request.loanDurationDays() == null ? 14 : request.loanDurationDays(),
                        clean(request.pickupPreferences()),
                        clean(request.cityOrPostalCode()),
                        now,
                        now))
                .toList();
        return bookRepository.saveAll(books).stream().map(this::toResponse).toList();
    }

    private BookResponse toResponse(BookDocument book) {
        UserDocument owner = userRepository.findById(book.ownerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "The book owner account could not be found."));
        return new BookResponse(
                book.id(),
                book.isbn(),
                book.title(),
                book.author(),
                book.genre(),
                book.language(),
                book.description(),
                book.publisher(),
                book.coverImageUrl(),
                book.condition(),
                book.status(),
                publicUser(owner),
                book.loanDurationDays(),
                book.pickupPreferences(),
                book.createdAt(),
                book.updatedAt());
    }

    private IncomingRequestResponse toIncomingRequest(BorrowRequestDocument request) {
        BookDocument book = findBook(request.bookId());
        UserDocument borrower = userRepository.findById(request.borrowerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "A borrower's account could not be found."));
        return new IncomingRequestResponse(
                request.id(),
                request.bookId(),
                book.title(),
                publicUser(borrower),
                request.desiredStartDate(),
                request.loanDurationDays(),
                request.personalMessage(),
                request.status(),
                request.createdAt());
    }

    private PublicUserResponse publicUser(UserDocument user) {
        return new PublicUserResponse(user.id(), user.displayName(), user.cityOrPostalCode());
    }

    private BookDocument findBook(String bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
    }

    private void requireOwner(BookDocument book, String userId) {
        if (!book.ownerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own books.");
        }
    }

    private BookCondition requiredCondition(BookCondition condition) {
        if (condition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book condition is required.");
        }
        return condition;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static class PatternSupport {
        private static String escape(String value) {
            return java.util.regex.Pattern.quote(value.toLowerCase(Locale.ROOT));
        }
    }
}
