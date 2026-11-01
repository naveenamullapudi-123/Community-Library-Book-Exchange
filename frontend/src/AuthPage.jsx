import { useState } from 'react';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

async function readAuthResponse(response) {
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'The request could not be completed.');
  }
  return data;
}

function AuthPage({ mode, notice, onAuthenticated, onNavigate }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isRegistering = mode === 'register';

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage('');

    const normalizedEmail = email.trim();
    if (!emailPattern.test(normalizedEmail)) {
      setMessage('Enter a valid email address.');
      return;
    }

    if (isRegistering && (name.trim().length < 2 || name.trim().length > 100)) {
      setMessage('Name must be between 2 and 100 characters.');
      return;
    }

    if (
      password.length < 8 ||
      new TextEncoder().encode(password).length > 72
    ) {
      setMessage('Password must be at least 8 characters and no more than 72 UTF-8 bytes.');
      return;
    }

    if (isRegistering && password !== confirmPassword) {
      setMessage('The passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch(
        isRegistering ? '/api/auth/register' : '/api/auth/login',
        {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(
            isRegistering
              ? { name: name.trim(), email: normalizedEmail, password }
              : { email: normalizedEmail, password },
          ),
        },
      );
      const result = await readAuthResponse(response);
      onAuthenticated(result.user);
    } catch (error) {
      setMessage(error.message || 'Unable to reach the authentication service.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card panel" aria-labelledby="auth-heading">
        <p className="eyebrow">Read, share, repeat</p>
        <h1 id="auth-heading">
          {isRegistering ? 'Join the library' : 'Welcome back'}
        </h1>
        <p className="auth-intro">
          {isRegistering
            ? 'Create an account to continue to the Community Library Book Exchange.'
            : 'Log in to explore the Community Library Book Exchange.'}
        </p>

        {notice && <p className="form-message error" role="alert">{notice}</p>}
        {message && <p className="form-message error" role="alert">{message}</p>}

        <form onSubmit={handleSubmit} noValidate>
          {isRegistering && (
            <label>
              Name
              <input
                name="name"
                autoComplete="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                minLength={2}
                maxLength={100}
                required
              />
            </label>
          )}
          <label>
            Email
            <input
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              maxLength={254}
              required
            />
          </label>
          <label>
            Password
            <input
              name="password"
              type="password"
              autoComplete={isRegistering ? 'new-password' : 'current-password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              required
            />
          </label>
          {isRegistering && (
            <label>
              Confirm password
              <input
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                minLength={8}
                required
              />
            </label>
          )}
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting
              ? (isRegistering ? 'Creating account...' : 'Logging in...')
              : (isRegistering ? 'Create account' : 'Log in')}
          </button>
        </form>

        <p className="auth-switch">
          {isRegistering ? 'Already have an account?' : 'New to the library?'}{' '}
          <button
            className="auth-link"
            type="button"
            onClick={() => onNavigate(isRegistering ? '/login' : '/register')}
          >
            {isRegistering ? 'Log in' : 'Create an account'}
          </button>
        </p>
      </section>
    </main>
  );
}

export default AuthPage;
