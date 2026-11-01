import { Router } from 'express';
import mongoose from 'mongoose';
import Book from '../models/Book.js';
import { requireAdmin, requireAuth } from '../middleware/auth.js';

const router = Router();

router.get('/', async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (
    req.query.availability !== undefined &&
    !['Available', 'Borrowed'].includes(req.query.availability)
  ) {
    return res.status(400).json({ message: 'Availability must be Available or Borrowed.' });
  }

  try {
    const filter = req.query.availability
      ? { availability: req.query.availability }
      : {};
    const books = await Book.find(filter).sort({ createdAt: -1 }).lean();
    return res.json(books);
  } catch (error) {
    return next(error);
  }
});

router.post('/', requireAuth, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!req.body || typeof req.body !== 'object' || Array.isArray(req.body)) {
    return res.status(400).json({ message: 'Provide book details as a JSON object.' });
  }

  try {
    const book = await Book.create(req.body);
    return res.status(201).json(book);
  } catch (error) {
    return next(error);
  }
});

router.patch('/:bookId/return', requireAuth, requireAdmin, async (req, res, next) => {
  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!mongoose.isValidObjectId(req.params.bookId)) {
    return res.status(400).json({ message: 'Choose a valid book to return.' });
  }

  try {
    const book = await Book.findOneAndUpdate(
      { _id: req.params.bookId, availability: 'Borrowed' },
      {
        $set: {
          availability: 'Available',
          lastReturnedAt: new Date(),
        },
        $unset: { currentBorrowRequest: 1 },
      },
      { new: true, runValidators: true },
    )
      .select('_id title availability')
      .lean();

    if (book) {
      return res.json({ message: `"${book.title}" has been marked as returned.`, book });
    }

    const existingBook = await Book.exists({ _id: req.params.bookId });
    if (!existingBook) {
      return res.status(404).json({ message: 'Book not found.' });
    }

    return res.status(409).json({ message: 'This book is already marked as available.' });
  } catch (error) {
    return next(error);
  }
});

export default router;
