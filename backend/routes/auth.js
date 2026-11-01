import { Router } from 'express';
import mongoose from 'mongoose';
import User from '../models/User.js';
import {
  createAuthToken,
  getAuthCookieOptions,
  getJwtSecret,
  requireAuth,
} from '../middleware/auth.js';

const router = Router();
const cookieName = 'community_library_token';
const tokenLifetimeMs = 60 * 60 * 1000;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isRequestBody(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function publicUser(user) {
  return {
    id: String(user._id),
    name: user.name,
    email: user.email,
    role: user.role ?? 'reader',
  };
}

function setAuthCookie(res, token) {
  res.cookie(cookieName, token, {
    ...getAuthCookieOptions(),
    maxAge: tokenLifetimeMs,
  });
}

router.post('/register', async (req, res, next) => {
  if (!getJwtSecret()) {
    return res.status(503).json({
      message: 'Authentication is not configured. Set a JWT_SECRET of at least 32 characters.',
    });
  }

  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!isRequestBody(req.body)) {
    return res.status(400).json({ message: 'Provide your name, email, and password as a JSON object.' });
  }

  const { name, email, password } = req.body;
  if (typeof name !== 'string' || name.trim().length < 2 || name.trim().length > 100) {
    return res.status(400).json({ message: 'Name must be between 2 and 100 characters.' });
  }

  if (
    typeof email !== 'string' ||
    email.trim().length > 254 ||
    !emailPattern.test(email.trim())
  ) {
    return res.status(400).json({ message: 'Enter a valid email address.' });
  }

  if (
    typeof password !== 'string' ||
    password.length < 8 ||
    Buffer.byteLength(password, 'utf8') > 72
  ) {
    return res.status(400).json({
      message: 'Password must be at least 8 characters and no more than 72 UTF-8 bytes.',
    });
  }

  try {
    const normalizedEmail = email.trim().toLowerCase();
    const user = await User.create({
      name: name.trim(),
      email: normalizedEmail,
      passwordHash: password,
      role: 'reader',
    });
    const token = createAuthToken(user._id);
    if (!token) {
      return res.status(503).json({
        message: 'Authentication is not configured. Set a JWT_SECRET of at least 32 characters.',
      });
    }

    setAuthCookie(res, token);
    return res.status(201).json({
      message: 'Your account has been created.',
      user: publicUser(user),
    });
  } catch (error) {
    if (error.code === 11000 && error.keyPattern?.email) {
      return res.status(409).json({ message: 'An account with this email already exists.' });
    }

    return next(error);
  }
});

router.post('/login', async (req, res, next) => {
  if (!getJwtSecret()) {
    return res.status(503).json({
      message: 'Authentication is not configured. Set a JWT_SECRET of at least 32 characters.',
    });
  }

  if (mongoose.connection.readyState !== 1) {
    return res.status(503).json({ message: 'The database is not connected yet.' });
  }

  if (!isRequestBody(req.body)) {
    return res.status(400).json({ message: 'Provide your email and password as a JSON object.' });
  }

  const { email, password } = req.body;
  if (
    typeof email !== 'string' ||
    email.trim().length > 254 ||
    !emailPattern.test(email.trim()) ||
    typeof password !== 'string' ||
    password.length === 0 ||
    Buffer.byteLength(password, 'utf8') > 72
  ) {
    return res.status(400).json({ message: 'Enter a valid email address and password.' });
  }

  try {
    const user = await User.findOne({ email: email.trim().toLowerCase() })
      .select('+passwordHash')
      .exec();
    if (!user || !(await user.comparePassword(password))) {
      return res.status(401).json({ message: 'Email or password is incorrect.' });
    }

    const token = createAuthToken(user._id);
    if (!token) {
      return res.status(503).json({
        message: 'Authentication is not configured. Set a JWT_SECRET of at least 32 characters.',
      });
    }

    setAuthCookie(res, token);
    return res.json({
      message: 'You are now logged in.',
      user: publicUser(user),
    });
  } catch (error) {
    return next(error);
  }
});

router.post('/logout', (_req, res) => {
  res.clearCookie(cookieName, getAuthCookieOptions());
  return res.json({ message: 'You are now logged out.' });
});

router.get('/me', requireAuth, (req, res) => {
  return res.json({ user: publicUser(req.user) });
});

export default router;
