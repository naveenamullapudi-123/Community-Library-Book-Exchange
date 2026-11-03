package com.bookbridge.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Service
public class BookCsvService {

    private static final Set<String> REQUIRED_HEADERS = Set.of("isbn", "title", "author", "condition");
    private static final int MAX_ROWS = 500;

    private final Validator validator;
    private final BookService bookService;

    public BookCsvService(Validator validator, BookService bookService) {
        this.validator = validator;
        this.bookService = bookService;
    }

    public List<BookResponse> upload(String ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Choose a non-empty CSV file.");
        }

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, Integer> headerIndexes = new HashMap<>();
            parser.getHeaderMap().forEach((header, index) ->
                    headerIndexes.put(header.trim().toLowerCase(Locale.ROOT), index));
            if (!headerIndexes.keySet().containsAll(REQUIRED_HEADERS)) {
                throw badRequest("CSV must include these headers: isbn, title, author, condition.");
            }
            if (headerIndexes.size() != parser.getHeaderMap().size()) {
                throw badRequest("CSV headers must be unique, ignoring letter case.");
            }

            List<BookRequest> requests = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (requests.size() + errors.size() >= MAX_ROWS) {
                    throw badRequest("A CSV upload can contain at most 500 book rows.");
                }
                if (record.size() > headerIndexes.size()) {
                    errors.add("Row " + record.getRecordNumber() + ": there are more values than CSV headers.");
                    continue;
                }
                try {
                    BookRequest request = toRequest(record, headerIndexes);
                    List<String> violations = validator.validate(request).stream()
                            .map(ConstraintViolation::getMessage)
                            .toList();
                    if (!violations.isEmpty()) {
                        errors.add("Row " + record.getRecordNumber() + ": " + String.join(", ", violations));
                    } else {
                        requests.add(request);
                    }
                } catch (IllegalArgumentException exception) {
                    errors.add("Row " + record.getRecordNumber() + ": " + exception.getMessage());
                }
            }
            if (requests.isEmpty() && errors.isEmpty()) {
                throw badRequest("The CSV file does not contain any book rows.");
            }
            if (!errors.isEmpty()) {
                throw badRequest("No books were imported. " + String.join(" ", errors));
            }
            return bookService.createAll(ownerId, requests);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the CSV file.");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The CSV file has missing or inconsistent columns.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The CSV file has invalid or duplicate column headers.");
        }
    }

    private BookRequest toRequest(CSVRecord record, Map<String, Integer> headerIndexes) {
        return new BookRequest(
                value(record, headerIndexes, "isbn"),
                value(record, headerIndexes, "title"),
                value(record, headerIndexes, "author"),
                value(record, headerIndexes, "genre"),
                value(record, headerIndexes, "language"),
                value(record, headerIndexes, "description"),
                value(record, headerIndexes, "publisher"),
                value(record, headerIndexes, "coverimageurl"),
                condition(value(record, headerIndexes, "condition")),
                status(value(record, headerIndexes, "status")),
                integer(value(record, headerIndexes, "loandurationdays")),
                value(record, headerIndexes, "pickuppreferences"),
                value(record, headerIndexes, "cityorpostalcode"));
    }

    private String value(CSVRecord record, Map<String, Integer> headerIndexes, String column) {
        Integer index = headerIndexes.get(column);
        return index == null || index >= record.size() ? null : blankToNull(record.get(index));
    }

    private BookCondition condition(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "EXCELLENT" -> BookCondition.EXCELLENT;
            case "GOOD" -> BookCondition.GOOD;
            case "ACCEPTABLE" -> BookCondition.ACCEPTABLE;
            case "WORN_BUT_READABLE" -> BookCondition.WORN_BUT_READABLE;
            default -> throw new IllegalArgumentException("condition must be Excellent, Good, Acceptable, or Worn but Readable.");
        };
    }

    private BookStatus status(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "AVAILABLE" -> BookStatus.AVAILABLE;
            case "LENT_OUT" -> BookStatus.LENT_OUT;
            case "UNAVAILABLE" -> BookStatus.UNAVAILABLE;
            default -> throw new IllegalArgumentException("status must be Available, Lent Out, or Unavailable.");
        };
    }

    private Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("loanDurationDays must be a whole number.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
