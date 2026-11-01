import jwt from 'jsonwebtoken';
import mongoose from 'mongoose';
import User from '../models/User.js';

const jwtIssuer = 'community-library-book-exchange';
const jwtAudience = 'community-library-client';

export function getJwtSecret() {
  const secret = process.env.JWT_SECRET;
  return typeof secret === 'string' && secret.length >= 32 ? secret : null;
}

export function getAuthCookieOptions() {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/api',
  };
}

export function createAuthToken(userId) {
  const secret = getJwtSecret();
  if (!secret) {
    return null;
  }

  return jwt.sign({}, secret, {
    subject: String(userId),
    issuer: jwtIssuer,
    audience: jwtAudience,
    expiresIn: '1h',
  });
}

export async function requireAuth(req, res, next) {
  const secret = getJwtSecret();
  if (!secret) {
    return res.status(503).json({
      message: 'Authentication is not configured. Set a JWT_SECRET of at least 32 characters.',
    });
  }

  const token = req.cookies?.community_library_token;
  if (!token) {
    return res.status(401).json({ message: 'Please log in to continue.' });
  }

  try {
    const payload = jwt.verify(token, secret, {
      issuer: jwtIssuer,
      audience: jwtAudience,
    });
    if (
      typeof payload !== 'object' ||
      typeof payload.sub !== 'string' ||
      !mongoose.isValidObjectId(payload.sub)
    ) {
      return res.status(401).json({ message: 'Your login session is invalid. Please log in again.' });
    }

    if (mongoose.connection.readyState !== 1) {
      return res.status(503).json({ message: 'The database is not connected yet.' });
    }

    const user = await User.findById(payload.sub).select('_id name email role').lean();
    if (!user) {
      return res.status(401).json({ message: 'Your account could not be found. Please log in again.' });
    }

    req.user = { ...user, role: user.role ?? 'reader' };
    return next();
  } catch (error) {
    if (error.name === 'JsonWebTokenError' || error.name === 'TokenExpiredError') {
      return res.status(401).json({ message: 'Your login session is invalid or expired. Please log in again.' });
    }

    return next(error);
  }
}

export function requireAdmin(req, res, next) {
  if (!req.user) {
    return res.status(401).json({ message: 'Please log in to continue.' });
  }

  if (req.user.role !== 'admin') {
    return res.status(403).json({ message: 'Admin access is required for this action.' });
  }

  return next();
}
