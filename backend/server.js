import 'dotenv/config';
import cookieParser from 'cookie-parser';
import express from 'express';
import mongoose from 'mongoose';
import authRouter from './routes/auth.js';
import booksRouter from './routes/books.js';
import borrowRequestsRouter from './routes/borrowRequests.js';

const app = express();
const port = Number(process.env.PORT) || 5000;
const mongoUri =
  process.env.MONGODB_URI ?? 'mongodb://127.0.0.1:27017/community_library';

app.use(express.json({ limit: '10kb' }));
app.use(cookieParser());

app.get('/api/health', (_req, res) => {
  res.json({
    server: 'running',
    database: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
  });
});

app.use('/api/books', booksRouter);
app.use('/api/borrow-requests', borrowRequestsRouter);
app.use('/api/auth', authRouter);

app.use((error, _req, res, _next) => {
  console.error('Request failed:', error.message);

  if (error.name === 'ValidationError') {
    return res.status(400).json({ message: error.message });
  }

  if (error.type === 'entity.parse.failed') {
    return res.status(400).json({ message: 'Request body must contain valid JSON.' });
  }

  return res.status(500).json({ message: 'The server could not complete the request.' });
});

app.listen(port, () => {
  console.log(`Backend server is running at http://localhost:${port}`);
});

mongoose
  .connect(mongoUri)
  .then(() => {
    console.log('Connected to MongoDB.');
  })
  .catch((error) => {
    console.error('Could not connect to MongoDB:', error.message);
  });
