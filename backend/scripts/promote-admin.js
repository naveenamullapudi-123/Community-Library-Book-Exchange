import 'dotenv/config';
import mongoose from 'mongoose';
import User from '../models/User.js';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const email = process.argv[2]?.trim().toLowerCase();

async function promoteSelectedUser() {
  if (process.argv.length !== 3 || !email || !emailPattern.test(email)) {
    throw new Error('Provide exactly one valid existing account email address.');
  }

  const mongoUri =
    process.env.MONGODB_URI ?? 'mongodb://127.0.0.1:27017/community_library';
  await mongoose.connect(mongoUri);

  const user = await User.findOneAndUpdate(
    { email },
    { $set: { role: 'admin' } },
    { new: true, runValidators: true },
  )
    .select('_id')
    .lean();

  if (!user) {
    throw new Error('No existing account matched the supplied email address.');
  }

  console.log('Admin role set for the selected account.');
}

try {
  await promoteSelectedUser();
} catch {
  console.error('Admin promotion failed. Check the supplied account and database connection.');
  process.exitCode = 1;
} finally {
  await mongoose.disconnect();
}
