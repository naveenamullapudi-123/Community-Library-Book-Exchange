package com.bookbridge.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.NotificationDocument;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.NotificationRepository;

@Service
public class DueDateReminderService {

    private static final Logger logger = LoggerFactory.getLogger(DueDateReminderService.class);
    private static final String REMINDER_TYPE = "LOAN_DUE_REMINDER";

    private final LoanRepository loanRepository;
    private final NotificationRepository notificationRepository;

    public DueDateReminderService(
            LoanRepository loanRepository,
            NotificationRepository notificationRepository) {
        this.loanRepository = loanRepository;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(cron = "${bookbridge.reminders.cron:0 5 8 * * *}")
    public void createDueDateReminders() {
        createDueDateReminders(LocalDate.now());
    }

    public int createDueDateReminders(LocalDate today) {
        LocalDate threeDaysFromNow = today.plusDays(3);
        List<LoanDocument> dueLoans = loanRepository.findByStatusAndDueDateIn(
                BorrowService.ACTIVE, List.of(threeDaysFromNow, today));
        int created = 0;
        for (LoanDocument loan : dueLoans) {
            String reminderDay = loan.dueDate().equals(today) ? "DUE_TODAY" : "DUE_IN_3_DAYS";
            String message = loan.dueDate().equals(today)
                    ? "Your borrowed book is due today."
                    : "Your borrowed book is due in three days.";
            for (String userId : List.of(loan.borrowerId(), loan.ownerId())) {
                String notificationId = loan.id() + ":" + reminderDay + ":" + userId;
                if (notificationRepository.existsById(notificationId)) {
                    continue;
                }
                try {
                    notificationRepository.save(new NotificationDocument(
                            notificationId,
                            userId,
                            REMINDER_TYPE,
                            message,
                            loan.id(),
                            false,
                            null,
                            Instant.now()));
                    created++;
                } catch (DuplicateKeyException exception) {
                    logger.debug("Due-date reminder {} was already created by another scheduler.", notificationId);
                }
            }
        }
        return created;
    }
}
