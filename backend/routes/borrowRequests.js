import { Router } from 'express';
import mongoose from 'mongoose';
import Book from '../models/Book.js';
import BorrowRequest from '../models/BorrowRequest.js';
import { requireAdmin, requireAuth } from '../middleware/auth.js';

const router = Router();

class RequestActionError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function isTransactionUnsupported(error) {
  return (
    error.code === 20 ||
    error.codeName === 'IllegalOperation' ||
    /Transaction numbers are only allowed|does not support transactions|replica set member or mongos/i.test(
      error.message,
    )
  );
}

async function findRequestStateError(requestId, session) {
  let query = BorrowRequest.findById(requestId).select('status');
  if (session) {
    query = query.session(session);
  }
  const request = await query.lean();

  if (!request) {
    return new RequestActionError(404, 'Borrow request not found.');
  }

  return new RequestActionError(
    409,
    `This request is already ${request.status.toLowerCase()} and cannot be processed again.`,
  );
}

async function approveWithTransaction(requestId) {
  const session = await mongoose.startSession();
  let updatedRequest;

  try {
    await session.withTransaction(async () => {
      const request = await BorrowRequest.findOne({
        _id: requestId,
        status: 'Pending',
      })
        .select('book')
        .session(session)
        .lean();

      if (!request) {
        throw await findRequestStateError(requestId, session);
      }

      const book = await Book.findOneAndUpdate(
        { _id: request.book, availability: 'Available' },
        {
          $set: {
            availability: 'Borrowed',
            currentBorrowRequest: request._id,
          },
        },
        { new: true, runValidators: true, session },
      )
        .select('_id')
        .lean();

      if (!book) {
        const existingBook = await Book.exists({ _id: request.book }).session(session);
        if (!existingBook) {
          throw new RequestActionError(404, 'The book for this request could not be found.');
        }

        throw new RequestActionError(
          409,
          'This book is already borrowed. The request cannot be approved.',
        );
      }

      updatedRequest = await BorrowRequest.findOneAndUpdate(
        { _id: request._id, status: 'Pending' },
        { $set: { status: 'Approved' } },
        { new: true, runValidators: true, session },
      )
        .select('book bookTitle requesterName status createdAt')
        .lean();

      if (!updatedRequest) {
        throw await findRequestStateError(requestId, session);
      }
    });

    return updatedRequest;
  } finally {
    await session.endSession();
  }
}

async function approveWithoutTransaction(requestId) {
  const request = await BorrowRequest.findOne({
    _id: requestId,
    status: 'Pending',
  })
    .select('book')
    .lean();

  if (!request) {
    throw await findRequestStateError(requestId);
  }

  const book = await Book.findOneAndUpdate(
    { _id: request.book, availability: 'Available' },
    {
      $set: {
        availability: 'Borrowed',
        currentBorrowRequest: request._id,
      },
    },
    { new: true, runValidators: true },
  )
    .select('_id')
    .lean();

  if (!book) {
    const existingBook = await Book.exists({ _id: request.book });
    if (!existingBook) {
      throw new RequestActionError(404, 'The book for this request could not be found.');
    }

    throw new RequestActionError(
      409,
      'This book is already borrowed. The request cannot be approved.',
    );
  }

  try {
    const updatedRequest = await BorrowRequest.findOneAndUpdate(
      { _id: request._id, status: 'Pending' },
      { $set: { status: 'Approved' } },
      { new: true, runValidators: true },
    )
      .select('book bookTitle requesterName status createdAt')
      .lean();

    if (updatedRequest) {
      return updatedRequest;
    }

    throw await findRequestStateError(requestId);
  } catch (error) {
    try {
      const rollback = await Book.updateOne(
        { _id: book._id, currentBorrowRequest: request._id },
        {
          $set: { availability: 'Available' },
          $unset: { currentBorrowRequest: 1 },
        },
      );
      if (rollback.matchedCount === 0) {
        const currentBook = await Book.findById(book._id).select('availability').lean();
        if (currentBook?.availability !== 'Available') {
          throw new Error('The claimed book could not be restored to Available.');
        }
      }
    } catch (rollbackError) {
      console.error(
        `Could not roll back book availability for request ${requestId}:`,
        rollbackError.message,
      );
      throw new Error(
        'The request could not be approved and the book rollback failed. Please check the book record.',
        { cause: error },
      );
    }

    throw error;
  }
}

router.get('/mine', requireAuth, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  try {
    const requests = await BorrowRequest.find({ user: req.user._id })
      .select('book bookTitle requesterName status createdAt')
      .populate({ path: 'book', select: 'availability' })
      .sort({ createdAt: -1 })
      .lean();

    return res.json(requests);
  } catch (error) {
    return next(error);
  }
});

router.get('/', requireAuth, requireAdmin, async (req, res, next) => {
  if (req.query.requesterName !== undefined) {
    return res.status(400).json({
      message: 'Name-based history lookup is no longer available. Use your signed-in account history.',
    });
  }

  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  try {
    const requests = await BorrowRequest.find()
      .select('book bookTitle requesterName status createdAt updatedAt')
      .sort({ createdAt: -1 })
      .lean();

    return res.json(requests);
  } catch (error) {
    return next(error);
  }
});

router.patch('/:requestId/status', requireAuth, requireAdmin, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!mongoose.isValidObjectId(req.params.requestId)) {
    return res.status(400).json({ message: 'Choose a valid borrow request.' });
  }

  if (!req.body || typeof req.body !== 'object' || Array.isArray(req.body)) {
    return res.status(400).json({ message: 'Provide the new status as a JSON object.' });
  }

  const { status } = req.body;
  if (!['Approved', 'Rejected'].includes(status)) {
    return res.status(400).json({ message: 'Status must be Approved or Rejected.' });
  }

  try {
    if (status === 'Approved') {
      let updatedRequest;
      try {
        updatedRequest = await approveWithTransaction(req.params.requestId);
      } catch (error) {
        if (!isTransactionUnsupported(error)) {
          throw error;
        }

        updatedRequest = await approveWithoutTransaction(req.params.requestId);
      }

      return res.json({
        message: 'Borrow request approved and book marked as borrowed.',
        request: updatedRequest,
      });
    }

    const updatedRequest = await BorrowRequest.findOneAndUpdate(
      { _id: req.params.requestId, status: 'Pending' },
      { $set: { status } },
      { new: true, runValidators: true },
    )
      .select('book bookTitle requesterName status createdAt')
      .lean();

    if (updatedRequest) {
      return res.json({
        message: `Borrow request ${status.toLowerCase()} successfully.`,
        request: updatedRequest,
      });
    }

    throw await findRequestStateError(req.params.requestId);
  } catch (error) {
    if (error instanceof RequestActionError) {
      return res.status(error.status).json({ message: error.message });
    }

    return next(error);
  }
});

router.patch('/:requestId/repair-book', requireAuth, requireAdmin, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!mongoose.isValidObjectId(req.params.requestId)) {
    return res.status(400).json({ message: 'Choose a valid borrow request.' });
  }

  try {
    const request = await BorrowRequest.findOne({
      _id: req.params.requestId,
      status: 'Approved',
    })
      .select('book bookTitle requesterName status createdAt updatedAt')
      .lean();

    if (!request) {
      const existingRequest = await BorrowRequest.exists({ _id: req.params.requestId });
      if (!existingRequest) {
        return res.status(404).json({ message: 'Borrow request not found.' });
      }

      return res.status(409).json({
        message: 'Only an approved request can repair book availability.',
      });
    }

    const newerApprovedRequest = await BorrowRequest.exists({
      book: request.book,
      status: 'Approved',
      updatedAt: { $gt: request.updatedAt },
    });
    if (newerApprovedRequest) {
      return res.status(409).json({
        message: 'A newer approved request exists for this book. This older request cannot be repaired.',
      });
    }

    const currentBook = await Book.findById(request.book)
      .select('_id title availability currentBorrowRequest lastReturnedAt updatedAt')
      .lean();

    if (!currentBook) {
      return res.status(404).json({ message: 'The book for this request could not be found.' });
    }

    if (currentBook.availability !== 'Available') {
      return res.status(409).json({
        message: 'This book is not available for repair; it may already be borrowed.',
      });
    }

    if (
      (currentBook.currentBorrowRequest &&
        String(currentBook.currentBorrowRequest) !== String(request._id)) ||
      (currentBook.lastReturnedAt &&
        currentBook.lastReturnedAt > request.updatedAt) ||
      currentBook.updatedAt > request.updatedAt
    ) {
      return res.status(409).json({
        message: 'This book was changed or returned after the request was approved. Its availability was not changed.',
      });
    }

    const book = await Book.findOneAndUpdate(
      {
        _id: request.book,
        availability: 'Available',
        updatedAt: { $lte: request.updatedAt },
        $and: [
          {
            $or: [
              { currentBorrowRequest: { $exists: false } },
              { currentBorrowRequest: null },
              { currentBorrowRequest: request._id },
            ],
          },
          {
            $or: [
              { lastReturnedAt: { $exists: false } },
              { lastReturnedAt: null },
              { lastReturnedAt: { $lte: request.updatedAt } },
            ],
          },
        ],
      },
      {
        $set: {
          availability: 'Borrowed',
          currentBorrowRequest: request._id,
        },
      },
      { new: true, runValidators: true },
    )
      .select('_id title availability currentBorrowRequest lastReturnedAt updatedAt')
      .lean();

    if (!book) {
      return res.status(409).json({
        message: 'The book changed while the repair was being checked. Refresh the page and review its current state.',
      });
    }

    return res.json({
      message: `Book availability repaired: "${book.title}" is now marked as borrowed.`,
      request,
      book,
    });
  } catch (error) {
    return next(error);
  }
});

router.post('/', requireAuth, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!req.body || typeof req.body !== 'object' || Array.isArray(req.body)) {
    return res.status(400).json({ message: 'Provide request details as a JSON object.' });
  }

  const { bookId } = req.body;
  if (!mongoose.isValidObjectId(bookId)) {
    return res.status(400).json({ message: 'Choose a valid book to request.' });
  }

  try {
    const book = await Book.findOne({ _id: bookId, availability: 'Available' })
      .select('_id title')
      .lean();

    if (!book) {
      const existingBook = await Book.exists({ _id: bookId });
      if (!existingBook) {
        return res.status(404).json({ message: 'That book could not be found.' });
      }

      return res.status(409).json({
        message: 'This book is already borrowed and cannot be requested right now.',
      });
    }

    const borrowRequest = await BorrowRequest.create({
      user: req.user._id,
      book: book._id,
      bookTitle: book.title,
      requesterName: req.user.name,
    });

    return res.status(201).json({
      message: `Your request to borrow "${book.title}" was submitted.`,
      request: borrowRequest,
    });
  } catch (error) {
    return next(error);
  }
});

export default router;
