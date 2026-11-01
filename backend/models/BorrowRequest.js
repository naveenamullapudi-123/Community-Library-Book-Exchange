import mongoose from 'mongoose';

const borrowRequestSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
    },
    book: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Book',
      required: [true, 'A book is required for a borrow request.'],
    },
    bookTitle: {
      type: String,
      required: [true, 'Book title is required.'],
      trim: true,
      maxlength: [150, 'Book title must be 150 characters or fewer.'],
    },
    requesterName: {
      type: String,
      required: [true, 'Your name is required.'],
      trim: true,
      minlength: [2, 'Name must be at least 2 characters.'],
      maxlength: [100, 'Name must be 100 characters or fewer.'],
    },
    status: {
      type: String,
      enum: ['Pending', 'Approved', 'Rejected', 'Declined'],
      default: 'Pending',
    },
  },
  { timestamps: true },
);

const BorrowRequest = mongoose.model('BorrowRequest', borrowRequestSchema);

export default BorrowRequest;
