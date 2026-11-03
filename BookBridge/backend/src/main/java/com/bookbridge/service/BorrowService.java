package com.bookbridge.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BorrowDecisionRequest;
import com.bookbridge.dto.BorrowRequestCreateRequest;
import com.bookbridge.dto.BorrowRequestResponse;
import com.bookbridge.dto.ExtensionCreateRequest;
import com.bookbridge.dto.ExtensionDecisionRequest;
import com.bookbridge.dto.ExtensionResponse;
import com.bookbridge.dto.LoanResponse;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.BookStatus;
import com.bookbridge.model.BorrowRequestDocument;
import com.bookbridge.model.ExtensionRequestDocument;
import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.BorrowRequestRepository;
import com.bookbridge.repository.ExtensionRequestRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.NotificationRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class BorrowService {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String ACTIVE = "ACTIVE";
    public static final String RETURNED = "RETURNED";

    private final BookRepository bookRepository;
    private final BorrowRequestRepository requestRepository;
    private final LoanRepository loanRepository;
    private final ExtensionRequestRepository extensionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public BorrowService(
            BookRepository bookRepository,
            BorrowRequestRepository requestRepository,
            LoanRepository loanRepository,
            ExtensionRequestRepository extensionRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            UserRepository userRepository,
            MongoTemplate mongoTemplate) {
        this.bookRepository = bookRepository;
        this.requestRepository = requestRepository;
        this.loanRepository = loanRepository;
        this.extensionRepository = extensionRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public BorrowRequestResponse requestBook(
            String borrowerId,
            String bookId,
            BorrowRequestCreateRequest request) {
        BookDocument book = findBook(bookId);
        if (book.status() != BookStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This book is not currently available.");
        }
        if (book.ownerId().equals(borrowerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot request your own book.");
        }
        if (request.desiredStartDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The desired start date cannot be in the past.");
        }
        boolean alreadyPending = requestRepository.findByBorrowerIdOrderByCreatedAtDesc(borrowerId).stream()
                .anyMatch(existing -> existing.bookId().equals(bookId) && PENDING.equals(existing.status()));
        if (alreadyPending) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You already have a pending request for this book.");
        }

        BorrowRequestDocument saved = requestRepository.save(new BorrowRequestDocument(
                null,
                bookId,
                book.ownerId(),
                borrowerId,
                request.desiredStartDate(),
                request.loanDurationDays(),
                clean(request.personalMessage()),
                PENDING,
                null,
                Instant.now()));
        notificationService.create(
                book.ownerId(),
                "BORROW_REQUEST",
                "Someone requested to borrow " + book.title() + ".",
                saved.id(),
                "borrow-request:" + saved.id());
        return toRequestResponse(saved);
    }

    public List<BorrowRequestResponse> incomingRequests(String ownerId) {
        return requestRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, PENDING).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    public List<BorrowRequestResponse> myRequests(String borrowerId) {
        return requestRepository.findByBorrowerIdOrderByCreatedAtDesc(borrowerId).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    public BorrowRequestResponse decide(
            String ownerId,
            String requestId,
            BorrowDecisionRequest decision) {
        if (decision.decision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose APPROVE or REJECT.");
        }
        BorrowRequestDocument request = findRequest(requestId);
        requireOwner(request, ownerId);
        if (!PENDING.equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This borrow request has already been decided.");
        }

        if (decision.decision() == BorrowDecisionRequest.Decision.REJECT) {
            BorrowRequestDocument rejected = transitionRequest(
                    requestId,
                    ownerId,
                    PENDING,
                    REJECTED,
                    clean(decision.responseMessage()));
            notificationService.create(
                    request.borrowerId(),
                    "BORROW_REQUEST_REJECTED",
                    "Your request to borrow " + findBook(request.bookId()).title() + " was declined.",
                    request.id(),
                    "borrow-decision:" + request.id());
            return toRequestResponse(rejected);
        }

        validateHandover(request, decision);
        transitionRequest(requestId, ownerId, PENDING, PROCESSING, null);
        BookDocument claimedBook = null;
        LoanDocument savedLoan = null;
        BorrowRequestDocument approved;
        try {
            claimedBook = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(request.bookId())
                            .and("ownerId").is(ownerId)
                            .and("status").is(BookStatus.AVAILABLE)),
                    new Update().set("status", BookStatus.LENT_OUT).set("updatedAt", Instant.now()),
                    FindAndModifyOptions.options().returnNew(true),
                    BookDocument.class);
            if (claimedBook == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This book is no longer available to lend.");
            }

            LocalDate dueDate = request.desiredStartDate().plusDays(request.loanDurationDays());
            savedLoan = loanRepository.save(new LoanDocument(
                    null,
                    request.id(),
                    request.bookId(),
                    request.ownerId(),
                    request.borrowerId(),
                    request.desiredStartDate(),
                    dueDate,
                    decision.handoverDate(),
                    decision.handoverLocation().trim(),
                    decision.exchangeMethod(),
                    ACTIVE,
                    null));
            approved = transitionRequest(
                    requestId, ownerId, PROCESSING, APPROVED, clean(decision.responseMessage()));
        } catch (RuntimeException exception) {
            if (savedLoan != null) {
                loanRepository.deleteById(savedLoan.id());
            }
            if (claimedBook != null) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(claimedBook.id())
                                .and("ownerId").is(ownerId)
                                .and("status").is(BookStatus.LENT_OUT)),
                        new Update().set("status", BookStatus.AVAILABLE).set("updatedAt", Instant.now()),
                        BookDocument.class);
            }
            releaseClaimedRequest(requestId, ownerId);
            throw exception;
        }
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("bookId").is(request.bookId()).and("status").is(PENDING)),
                new Update().set("status", REJECTED)
                        .set("responseMessage", "The book was approved for another borrower."),
                BorrowRequestDocument.class);
        notificationService.create(
                request.borrowerId(),
                "BORROW_REQUEST_APPROVED",
                "Your request to borrow " + claimedBook.title() + " was approved.",
                savedLoan.id(),
                "borrow-decision:" + request.id());
        return toRequestResponse(approved);
    }

    public List<LoanResponse> borrowedLoans(String borrowerId) {
        return loanRepository.findByBorrowerIdAndStatusOrderByDueDateAsc(borrowerId, ACTIVE).stream()
                .map(this::toLoanResponse)
                .toList();
    }

    public List<LoanResponse> lentLoans(String ownerId) {
        return loanRepository.findByOwnerIdAndStatusOrderByDueDateAsc(ownerId, ACTIVE).stream()
                .map(this::toLoanResponse)
                .toList();
    }

    public List<LoanResponse> returnedLoans(String userId) {
        List<LoanDocument> borrowed = loanRepository.findByBorrowerIdAndStatusOrderByStartDateDesc(userId, RETURNED);
        List<LoanDocument> lent = loanRepository.findByOwnerIdAndStatusOrderByStartDateDesc(userId, RETURNED);
        return java.util.stream.Stream.concat(borrowed.stream(), lent.stream())
                .sorted(Comparator.comparing(LoanDocument::startDate).reversed())
                .map(this::toLoanResponse)
                .toList();
    }

    public LoanResponse getLoan(String loanId) {
        return toLoanResponse(findLoan(loanId));
    }

    public ExtensionResponse requestExtension(
            String borrowerId,
            String loanId,
            ExtensionCreateRequest request) {
        if (request.additionalDays() == null || request.additionalDays() < 1 || request.additionalDays() > 365) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "An extension must add between 1 and 365 days.");
        }
        LoanDocument loan = findLoan(loanId);
        if (!loan.borrowerId().equals(borrowerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the borrower can request an extension.");
        }
        if (!ACTIVE.equals(loan.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active loan can be extended.");
        }
        if (extensionRepository.existsByLoanIdAndStatus(loanId, PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "There is already a pending extension request for this loan.");
        }
        ExtensionRequestDocument extension = extensionRepository.save(new ExtensionRequestDocument(
                null,
                loan.id(),
                loan.bookId(),
                loan.ownerId(),
                loan.borrowerId(),
                request.additionalDays(),
                clean(request.message()),
                PENDING,
                null,
                Instant.now(),
                Instant.now()));
        notificationService.create(
                loan.ownerId(),
                "EXTENSION_REQUEST",
                "A borrower requested more time for " + findBook(loan.bookId()).title() + ".",
                extension.id(),
                "extension-request:" + extension.id());
        return toExtensionResponse(extension);
    }

    public List<ExtensionResponse> incomingExtensions(String ownerId) {
        return extensionRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, PENDING).stream()
                .map(this::toExtensionResponse)
                .toList();
    }

    public ExtensionResponse decideExtension(
            String ownerId,
            String extensionId,
            ExtensionDecisionRequest decision) {
        ExtensionRequestDocument extension = findExtension(extensionId);
        if (!extension.ownerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the book owner can decide this extension.");
        }
        if (decision.decision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose APPROVE or REJECT.");
        }
        if (!PENDING.equals(extension.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This extension request has already been decided.");
        }
        if (decision.decision() == ExtensionDecisionRequest.Decision.REJECT) {
            ExtensionRequestDocument rejected = transitionExtension(
                    extensionId, ownerId, PENDING, REJECTED, clean(decision.responseMessage()));
            notificationService.create(
                    extension.borrowerId(),
                    "EXTENSION_REJECTED",
                    "Your loan extension request was declined.",
                    extension.id(),
                    "extension-decision:" + extension.id());
            return toExtensionResponse(rejected);
        }

        LoanDocument currentLoan = findLoan(extension.loanId());
        if (!ACTIVE.equals(currentLoan.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The loan is no longer active and cannot be extended.");
        }
        transitionExtension(extensionId, ownerId, PENDING, PROCESSING, null);
        LoanDocument extendedLoan = null;
        ExtensionRequestDocument approved;
        try {
            extendedLoan = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(extension.loanId())
                            .and("ownerId").is(ownerId)
                            .and("status").is(ACTIVE)
                            .and("dueDate").is(currentLoan.dueDate())),
                    new Update().set(
                            "dueDate",
                            currentLoan.dueDate().plusDays(extension.additionalDays())),
                    FindAndModifyOptions.options().returnNew(true),
                    LoanDocument.class);
            if (extendedLoan == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The loan is no longer active and cannot be extended.");
            }
            approved = transitionExtension(
                    extensionId, ownerId, PROCESSING, APPROVED, clean(decision.responseMessage()));
        } catch (RuntimeException exception) {
            if (extendedLoan != null) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(extension.loanId())
                                .and("status").is(ACTIVE)
                                .and("dueDate").is(extendedLoan.dueDate())),
                        new Update().set("dueDate", currentLoan.dueDate()),
                        LoanDocument.class);
            }
            releaseClaimedExtension(extensionId, ownerId);
            throw exception;
        }
        notificationRepository.deleteByRelatedEntityIdAndType(extension.loanId(), "LOAN_DUE_REMINDER");
        notificationService.create(
                extension.borrowerId(),
                "EXTENSION_APPROVED",
                "Your loan extension was approved. The due date has been updated.",
                extension.id(),
                "extension-decision:" + extension.id());
        return toExtensionResponse(approved);
    }

    public LoanResponse markReturned(String userId, String loanId) {
        LoanDocument loan = findLoan(loanId);
        if (!loan.ownerId().equals(userId) && !loan.borrowerId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the borrower or book owner can mark this loan returned.");
        }
        LoanDocument returned = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(loanId)
                        .and("status").is(ACTIVE)
                        .orOperator(Criteria.where("ownerId").is(userId), Criteria.where("borrowerId").is(userId))),
                new Update().set("status", RETURNED).set("returnedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                LoanDocument.class);
        if (returned == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This loan was already returned or is no longer active.");
        }

        BookDocument book = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(returned.bookId())
                        .and("ownerId").is(returned.ownerId())
                        .and("status").is(BookStatus.LENT_OUT)),
                new Update().set("status", BookStatus.AVAILABLE).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                BookDocument.class);
        if (book == null) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(returned.id()).and("status").is(RETURNED)),
                    new Update().set("status", ACTIVE).unset("returnedAt"),
                    LoanDocument.class);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The loan was returned, but its book listing could not be made available.");
        }
        notificationService.create(
                returned.ownerId(),
                "LOAN_RETURNED",
                "The book has been marked as returned. You can now review this exchange.",
                returned.id(),
                "loan-return:" + returned.id());
        notificationService.create(
                returned.borrowerId(),
                "LOAN_RETURNED",
                "The book has been marked as returned. You can now review this exchange.",
                returned.id(),
                "loan-return:" + returned.id());
        notificationService.notifyWishlist(book.isbn(), book.title(), book.id(), book.updatedAt());
        return toLoanResponse(returned);
    }

    private BorrowRequestDocument transitionRequest(
            String requestId,
            String ownerId,
            String expectedStatus,
            String nextStatus,
            String responseMessage) {
        BorrowRequestDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(requestId)
                        .and("ownerId").is(ownerId)
                        .and("status").is(expectedStatus)),
                new Update().set("status", nextStatus)
                        .set("responseMessage", responseMessage),
                FindAndModifyOptions.options().returnNew(true),
                BorrowRequestDocument.class);
        if (updated == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This borrow request has already been decided.");
        }
        return updated;
    }

    private ExtensionRequestDocument transitionExtension(
            String extensionId,
            String ownerId,
            String expectedStatus,
            String nextStatus,
            String responseMessage) {
        ExtensionRequestDocument updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(extensionId)
                        .and("ownerId").is(ownerId)
                        .and("status").is(expectedStatus)),
                new Update().set("status", nextStatus)
                        .set("responseMessage", responseMessage)
                        .set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                ExtensionRequestDocument.class);
        if (updated == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This extension request has already been decided.");
        }
        return updated;
    }

    private void releaseClaimedRequest(String requestId, String ownerId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(requestId)
                        .and("ownerId").is(ownerId)
                        .and("status").is(PROCESSING)),
                new Update().set("status", PENDING),
                BorrowRequestDocument.class);
    }

    private void releaseClaimedExtension(String extensionId, String ownerId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(extensionId)
                        .and("ownerId").is(ownerId)
                        .and("status").is(PROCESSING)),
                new Update().set("status", PENDING).set("updatedAt", Instant.now()),
                ExtensionRequestDocument.class);
    }

    private void validateHandover(BorrowRequestDocument request, BorrowDecisionRequest decision) {
        if (decision.exchangeMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an exchange method.");
        }
        if (decision.handoverDate() == null || decision.handoverDate().isBefore(request.desiredStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The handover date must be on or after the requested start date.");
        }
        if (decision.handoverLocation() == null || decision.handoverLocation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an agreed handover location.");
        }
    }

    private BorrowRequestResponse toRequestResponse(BorrowRequestDocument request) {
        BookDocument book = findBook(request.bookId());
        return new BorrowRequestResponse(
                request.id(),
                request.bookId(),
                book.title(),
                request.ownerId(),
                request.borrowerId(),
                request.desiredStartDate(),
                request.loanDurationDays(),
                request.personalMessage(),
                request.status(),
                request.responseMessage(),
                request.createdAt());
    }

    private LoanResponse toLoanResponse(LoanDocument loan) {
        BookDocument book = findBook(loan.bookId());
        UserDocument owner = findUser(loan.ownerId());
        UserDocument borrower = findUser(loan.borrowerId());
        return new LoanResponse(
                loan.id(),
                loan.requestId(),
                loan.bookId(),
                book.title(),
                owner.id(),
                owner.displayName(),
                borrower.id(),
                borrower.displayName(),
                loan.startDate(),
                loan.dueDate(),
                loan.handoverDate(),
                loan.handoverLocation(),
                loan.exchangeMethod(),
                loan.status(),
                loan.returnedAt());
    }

    private ExtensionResponse toExtensionResponse(ExtensionRequestDocument extension) {
        BookDocument book = findBook(extension.bookId());
        UserDocument borrower = findUser(extension.borrowerId());
        return new ExtensionResponse(
                extension.id(),
                extension.loanId(),
                extension.bookId(),
                book.title(),
                borrower.id(),
                borrower.displayName(),
                extension.additionalDays(),
                extension.message(),
                extension.status(),
                extension.responseMessage(),
                extension.createdAt());
    }

    private BookDocument findBook(String bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
    }

    private BorrowRequestDocument findRequest(String requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrow request not found."));
    }

    private LoanDocument findLoan(String loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found."));
    }

    private ExtensionRequestDocument findExtension(String extensionId) {
        return extensionRepository.findById(extensionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extension request not found."));
    }

    private UserDocument findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found."));
    }

    private void requireOwner(BorrowRequestDocument request, String ownerId) {
        if (!request.ownerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the book owner can manage this request.");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
