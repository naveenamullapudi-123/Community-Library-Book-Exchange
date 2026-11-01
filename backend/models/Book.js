import mongoose from 'mongoose';

const bookSchema = new mongoose.Schema(
  {
    title: {
      type: String,
      required: [true, 'Title is required.'],
      trim: true,
      maxlength: [150, 'Title must be 150 characters or fewer.'],
    },
    author: {
      type: String,
      required: [true, 'Author is required.'],
      trim: true,
      maxlength: [120, 'Author must be 120 characters or fewer.'],
    },
    isbn: {
      type: String,
      required: [true, 'ISBN is required.'],
      trim: true,
      validate: {
        validator: (value) => {
          const normalizedIsbn = value.replace(/[\s-]/g, '');
          return /^(?:\d{9}[\dXx]|\d{13})$/.test(normalizedIsbn);
        },
        message: 'Enter a valid ISBN-10 or ISBN-13.',
      },
    },
    genre: {
      type: String,
      required: [true, 'Genre is required.'],
      trim: true,
      maxlength: [60, 'Genre must be 60 characters or fewer.'],
    },
    language: {
      type: String,
      required: [true, 'Language is required.'],
      trim: true,
      maxlength: [60, 'Language must be 60 characters or fewer.'],
    },
    description: {
      type: String,
      trim: true,
      maxlength: [1000, 'Description must be 1000 characters or fewer.'],
      default: '',
    },
    condition: {
      type: String,
      required: [true, 'Condition is required.'],
      enum: ['Like new', 'Good', 'Fair', 'Needs repair'],
    },
    availability: {
      type: String,
      required: [true, 'Availability is required.'],
      enum: ['Available', 'Borrowed'],
      default: 'Available',
    },
    currentBorrowRequest: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'BorrowRequest',
    },
    lastReturnedAt: {
      type: Date,
    },
  },
  { timestamps: true },
);

const Book = mongoose.model('Book', bookSchema);

export default Book;
