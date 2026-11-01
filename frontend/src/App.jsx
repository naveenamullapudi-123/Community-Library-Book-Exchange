import { useEffect, useState } from 'react';
import AuthPage from './AuthPage.jsx';

const initialForm = {
  title: '',
  author: '',
  isbn: '',
  genre: '',
  language: 'English',
  description: '',
  condition: 'Good',
  availability: 'Available',
};

const initialFilters = {
  search: '',
  genre: '',
  language: '',
  condition: '',
  availability: '',
};

async function readResponse(response) {
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(data.message || 'Your login session has expired. Please log in again.');
    }

    if (response.status === 403) {
      throw new Error(data.message || 'You do not have permission to perform this action.');
    }

    throw new Error(data.message || 'The request could not be completed.');
  }

  return data;
}

function App() {
  const [authUser, setAuthUser] = useState(null);
  const [isCheckingAuth, setIsCheckingAuth] = useState(true);
  const [authPath, setAuthPath] = useState(() => window.location.pathname);
  const [authNotice, setAuthNotice] = useState('');
  const [authActionMessage, setAuthActionMessage] = useState('');
  const [books, setBooks] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [filters, setFilters] = useState(initialFilters);
  const [status, setStatus] = useState('Checking the backend...');
  const [catalogError, setCatalogError] = useState('');
  const [formMessage, setFormMessage] = useState({ type: '', text: '' });
  const [activeRequestBookId, setActiveRequestBookId] = useState('');
  const [requestMessage, setRequestMessage] = useState({ bookId: '', type: '', text: '' });
  const [isSubmittingRequest, setIsSubmittingRequest] = useState(false);
  const [myBorrowRequests, setMyBorrowRequests] = useState([]);
  const [isLoadingRequests, setIsLoadingRequests] = useState(false);
  const [requestHistoryError, setRequestHistoryError] = useState('');
  const [adminRequests, setAdminRequests] = useState([]);
  const [adminRequestsError, setAdminRequestsError] = useState('');
  const [adminRequestsMessage, setAdminRequestsMessage] = useState({ type: '', text: '' });
  const [isLoadingAdminRequests, setIsLoadingAdminRequests] = useState(true);
  const [updatingRequestId, setUpdatingRequestId] = useState('');
  const [borrowedBooks, setBorrowedBooks] = useState([]);
  const [borrowedBooksError, setBorrowedBooksError] = useState('');
  const [isLoadingBorrowedBooks, setIsLoadingBorrowedBooks] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  const matchingBooks = books.filter((book) => {
    const searchTerm = filters.search.trim().toLocaleLowerCase();
    const matchesSearch =
      !searchTerm ||
      book.title.toLocaleLowerCase().includes(searchTerm) ||
      book.author.toLocaleLowerCase().includes(searchTerm);

    return (
      matchesSearch &&
      (!filters.genre || book.genre === filters.genre) &&
      (!filters.language || book.language === filters.language) &&
      (!filters.condition || book.condition === filters.condition) &&
      (!filters.availability || book.availability === filters.availability)
    );
  });
  const hasActiveFilters = Object.values(filters).some(Boolean);
  const isAdmin = authUser?.role === 'admin';

  function navigate(path) {
    window.history.pushState({}, '', path);
    setAuthPath(path);
    setAuthNotice('');
  }

  useEffect(() => {
    let isActive = true;
    const handlePopState = () => setAuthPath(window.location.pathname);
    window.addEventListener('popstate', handlePopState);

    async function checkCurrentUser() {
      try {
        const response = await fetch('/api/auth/me', { credentials: 'include' });
        if (response.status === 401) {
          return;
        }

        const data = await readResponse(response);
        if (isActive) {
          setAuthUser(data.user);
        }
      } catch (error) {
        if (isActive) {
          setAuthNotice(error.message || 'Could not check your login session.');
        }
      } finally {
        if (isActive) {
          setIsCheckingAuth(false);
        }
      }
    }

    checkCurrentUser();
    return () => {
      isActive = false;
      window.removeEventListener('popstate', handlePopState);
    };
  }, []);

  useEffect(() => {
    if (!authUser) {
      return;
    }

    loadBooks();
    loadMyBorrowingHistory();
    if (authUser.role === 'admin') {
      loadAdminRequests();
      loadBorrowedBooks();
    } else {
      setIsLoadingAdminRequests(false);
      setIsLoadingBorrowedBooks(false);
    }
  }, [authUser]);

  function handleAuthenticated(user) {
    setAuthUser(user);
    setAuthNotice('');
    setAuthActionMessage('');
    navigate('/');
  }

  async function handleLogout() {
    setAuthActionMessage('');
    try {
      await readResponse(
        await fetch('/api/auth/logout', {
          method: 'POST',
          credentials: 'include',
        }),
      );
      setAuthUser(null);
      setBooks([]);
      setMyBorrowRequests([]);
      setAdminRequests([]);
      setBorrowedBooks([]);
      setRequestHistoryError('');
      navigate('/login');
    } catch (error) {
      setAuthActionMessage(error.message || 'Could not log out. Please try again.');
    }
  }

  function getFilterOptions(field) {
    return [...new Set(books.map((book) => book[field]).filter(Boolean))].sort(
      (first, second) => first.localeCompare(second),
    );
  }

  function canRepairAvailability(request) {
    const book = books.find((item) => String(item._id) === String(request.book));
    const requestUpdatedAt = Date.parse(request.updatedAt);
    const bookUpdatedAt = Date.parse(book?.updatedAt);
    const lastReturnedAt = book?.lastReturnedAt
      ? Date.parse(book.lastReturnedAt)
      : null;

    return (
      request.status === 'Approved' &&
      book?.availability === 'Available' &&
      Number.isFinite(requestUpdatedAt) &&
      Number.isFinite(bookUpdatedAt) &&
      bookUpdatedAt <= requestUpdatedAt &&
      (lastReturnedAt === null ||
        (Number.isFinite(lastReturnedAt) && lastReturnedAt <= requestUpdatedAt))
    );
  }

  async function loadBooks() {
    setCatalogError('');

    try {
      const savedBooks = await readResponse(await fetch('/api/books'));
      setBooks(savedBooks);
    } catch (error) {
      setCatalogError(error.message);
    } finally {
      setIsLoading(false);
    }
  }

  async function loadAdminRequests() {
    setAdminRequestsError('');
    setIsLoadingAdminRequests(true);

    try {
      const requests = await readResponse(
        await fetch('/api/borrow-requests', { credentials: 'include' }),
      );
      setAdminRequests(requests);
    } catch (error) {
      setAdminRequestsError(error.message);
    } finally {
      setIsLoadingAdminRequests(false);
    }
  }

  async function loadBorrowedBooks() {
    setBorrowedBooksError('');
    setIsLoadingBorrowedBooks(true);

    try {
      const savedBooks = await readResponse(
        await fetch('/api/books?availability=Borrowed'),
      );
      setBorrowedBooks(savedBooks);
    } catch (error) {
      setBorrowedBooksError(error.message);
    } finally {
      setIsLoadingBorrowedBooks(false);
    }
  }

  async function loadMyBorrowingHistory() {
    setRequestHistoryError('');
    setIsLoadingRequests(true);

    try {
      const requests = await readResponse(
        await fetch('/api/borrow-requests/mine', { credentials: 'include' }),
      );
      setMyBorrowRequests(requests);
    } catch (error) {
      setRequestHistoryError(error.message);
    } finally {
      setIsLoadingRequests(false);
    }
  }

  async function refreshBorrowingHistory() {
    await loadMyBorrowingHistory();
  }

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`);
        }

        return response.json();
      })
      .then((data) => {
        setStatus(`Backend ${data.server}. MongoDB ${data.database}.`);
      })
      .catch(() => {
        setStatus('Backend not available yet. Start the backend terminal to connect.');
      });

  }, []);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((currentForm) => ({ ...currentForm, [name]: value }));
  }

  function handleFilterChange(event) {
    const { name, value } = event.target;
    setFilters((currentFilters) => ({ ...currentFilters, [name]: value }));
  }

  function clearFilters() {
    setFilters(initialFilters);
  }

  function openBorrowRequest(bookId) {
    setActiveRequestBookId(bookId);
    setRequestMessage({ bookId: '', type: '', text: '' });
  }

  function closeBorrowRequest() {
    setActiveRequestBookId('');
  }

  async function handleAdminRequestStatus(request, status) {
    setAdminRequestsMessage({ type: '', text: '' });
    setUpdatingRequestId(request._id);

    try {
      const result = await readResponse(
        await fetch(`/api/borrow-requests/${request._id}/status`, {
          method: 'PATCH',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status }),
        }),
      );
      setAdminRequests((currentRequests) =>
        currentRequests.map((item) =>
          item._id === result.request._id ? result.request : item,
        ),
      );
      setMyBorrowRequests((currentRequests) =>
        currentRequests.map((item) =>
          item._id === result.request._id ? result.request : item,
        ),
      );
      if (status === 'Approved' && result.request.book) {
        setBooks((currentBooks) =>
          currentBooks.map((book) =>
            book._id === result.request.book
              ? { ...book, availability: 'Borrowed' }
              : book,
          ),
        );
      }
      await Promise.all([
        loadBooks(),
        loadAdminRequests(),
        loadBorrowedBooks(),
        refreshBorrowingHistory(),
      ]);
      setAdminRequestsMessage({
        type: 'success',
        text: result.message,
      });
    } catch (error) {
      setAdminRequestsMessage({ type: 'error', text: error.message });
      if (error.message.includes('already')) {
        await loadAdminRequests();
      }
    } finally {
      setUpdatingRequestId('');
    }
  }

  async function handleRepairAvailability(request) {
    setAdminRequestsMessage({ type: '', text: '' });
    setUpdatingRequestId(request._id);

    try {
      const result = await readResponse(
        await fetch(`/api/borrow-requests/${request._id}/repair-book`, {
          method: 'PATCH',
          credentials: 'include',
        }),
      );
      setAdminRequests((currentRequests) =>
        currentRequests.map((item) =>
          item._id === result.request._id ? result.request : item,
        ),
      );
      await Promise.all([
        loadBooks(),
        loadAdminRequests(),
        loadBorrowedBooks(),
        refreshBorrowingHistory(),
      ]);
      setAdminRequestsMessage({ type: 'success', text: result.message });
    } catch (error) {
      setAdminRequestsMessage({ type: 'error', text: error.message });
      await Promise.all([
        loadBooks(),
        loadAdminRequests(),
        loadBorrowedBooks(),
        refreshBorrowingHistory(),
      ]);
    } finally {
      setUpdatingRequestId('');
    }
  }

  async function handleMarkReturned(book) {
    setAdminRequestsMessage({ type: '', text: '' });
    setUpdatingRequestId(book._id);

    try {
      const result = await readResponse(
        await fetch(`/api/books/${book._id}/return`, {
          method: 'PATCH',
          credentials: 'include',
        }),
      );
      setBooks((currentBooks) =>
        currentBooks.map((item) =>
          item._id === result.book._id ? { ...item, ...result.book } : item,
        ),
      );
      await Promise.all([
        loadBooks(),
        loadAdminRequests(),
        loadBorrowedBooks(),
        refreshBorrowingHistory(),
      ]);
      setAdminRequestsMessage({ type: 'success', text: result.message });
    } catch (error) {
      setAdminRequestsMessage({ type: 'error', text: error.message });
      if (error.message.includes('already')) {
        await loadBooks();
      }
    } finally {
      setUpdatingRequestId('');
    }
  }

  async function handleBorrowRequest(event, book) {
    event.preventDefault();
    setRequestMessage({ bookId: book._id, type: '', text: '' });

    if (book.availability !== 'Available') {
      setRequestMessage({
        bookId: book._id,
        type: 'error',
        text: 'This book is not available to request.',
      });
      return;
    }

    setIsSubmittingRequest(true);
    try {
      const result = await readResponse(
        await fetch('/api/borrow-requests', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ bookId: book._id }),
        }),
      );
      setRequestMessage({
        bookId: book._id,
        type: 'success',
        text: result.message || 'Your borrow request was submitted.',
      });
      closeBorrowRequest();
      await loadMyBorrowingHistory();
    } catch (error) {
      setRequestMessage({ bookId: book._id, type: 'error', text: error.message });
    } finally {
      setIsSubmittingRequest(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setFormMessage({ type: '', text: '' });

    const normalizedIsbn = form.isbn.replace(/[\s-]/g, '');
    if (!/^(?:\d{9}[\dXx]|\d{13})$/.test(normalizedIsbn)) {
      setFormMessage({
        type: 'error',
        text: 'Enter an ISBN-10 or ISBN-13. Spaces and hyphens are okay.',
      });
      return;
    }

    setIsSaving(true);
    try {
      const savedBook = await readResponse(
        await fetch('/api/books', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(form),
        }),
      );
      setBooks((currentBooks) => [savedBook, ...currentBooks]);
      setForm(initialForm);
      setFormMessage({ type: 'success', text: 'Book added to the catalog.' });
    } catch (error) {
      setFormMessage({ type: 'error', text: error.message });
    } finally {
      setIsSaving(false);
    }
  }

  if (isCheckingAuth) {
    return (
      <main className="auth-page">
        <p className="catalog-note" role="status">Checking your login...</p>
      </main>
    );
  }

  if (!authUser) {
    const mode = authPath === '/register' ? 'register' : 'login';
    return (
      <AuthPage
        key={mode}
        mode={mode}
        notice={authNotice}
        onAuthenticated={handleAuthenticated}
        onNavigate={navigate}
      />
    );
  }

  return (
    <main className="page">
      <header className="site-header">
        <p className="eyebrow">Read, share, repeat</p>
        <h1>Community Library Book Exchange</h1>
        <p className="intro">
          A friendly place to pass along a good book and find your next favorite.
        </p>
        <div className="account-bar">
          <p>Signed in as <strong>{authUser.name}</strong></p>
          <button className="clear-filters-button" type="button" onClick={handleLogout}>
            Log out
          </button>
        </div>
        {authActionMessage && (
          <p className="form-message error" role="alert">{authActionMessage}</p>
        )}
        <p className="status" role="status">{status}</p>
      </header>

      <div className="content-grid">
        <section className="panel" aria-labelledby="add-book-heading">
          <div className="section-heading">
            <p className="eyebrow">Share a story</p>
            <h2 id="add-book-heading">Add a book</h2>
          </div>

          <form onSubmit={handleSubmit}>
            <label>
              Title
              <input
                name="title"
                value={form.title}
                onChange={handleChange}
                maxLength={150}
                required
              />
            </label>
            <label>
              Author
              <input
                name="author"
                value={form.author}
                onChange={handleChange}
                maxLength={120}
                required
              />
            </label>
            <label>
              ISBN
              <input
                name="isbn"
                value={form.isbn}
                onChange={handleChange}
                inputMode="numeric"
                maxLength={20}
                placeholder="10 or 13 digits"
                required
              />
            </label>
            <div className="form-row">
              <label>
                Genre
                <input
                  name="genre"
                  value={form.genre}
                  onChange={handleChange}
                  maxLength={60}
                  placeholder="e.g. Fiction"
                  required
                />
              </label>
              <label>
                Language
                <input
                  name="language"
                  value={form.language}
                  onChange={handleChange}
                  maxLength={60}
                  required
                />
              </label>
            </div>
            <label>
              Description <span className="optional">(optional)</span>
              <textarea
                name="description"
                value={form.description}
                onChange={handleChange}
                maxLength={1000}
                rows={3}
              />
            </label>
            <div className="form-row">
              <label>
                Condition
                <select name="condition" value={form.condition} onChange={handleChange}>
                  <option>Like new</option>
                  <option>Good</option>
                  <option>Fair</option>
                  <option>Needs repair</option>
                </select>
              </label>
              <label>
                Availability
                <select
                  name="availability"
                  value={form.availability}
                  onChange={handleChange}
                >
                  <option>Available</option>
                  <option>Borrowed</option>
                </select>
              </label>
            </div>
            <button type="submit" disabled={isSaving}>
              {isSaving ? 'Saving book...' : 'Add book'}
            </button>
            {formMessage.text && (
              <p className={`form-message ${formMessage.type}`} role="status">
                {formMessage.text}
              </p>
            )}
          </form>
        </section>

        <section className="catalog" aria-labelledby="catalog-heading">
          <div className="section-heading catalog-heading">
            <div>
              <p className="eyebrow">Find your next read</p>
              <h2 id="catalog-heading">Book catalog</h2>
            </div>
            <span className="book-count" aria-live="polite">
              {matchingBooks.length} matching {matchingBooks.length === 1 ? 'book' : 'books'}
            </span>
          </div>

          {!isLoading && !catalogError && (
            <div className="filter-panel" aria-label="Search and filter books">
              <label>
                Search by title or author
                <input
                  type="search"
                  name="search"
                  value={filters.search}
                  onChange={handleFilterChange}
                  placeholder="Type a title or author"
                />
              </label>
              <div className="filter-row">
                <label>
                  Genre
                  <select name="genre" value={filters.genre} onChange={handleFilterChange}>
                    <option value="">All genres</option>
                    {getFilterOptions('genre').map((genre) => (
                      <option key={genre} value={genre}>{genre}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Language
                  <select
                    name="language"
                    value={filters.language}
                    onChange={handleFilterChange}
                  >
                    <option value="">All languages</option>
                    {getFilterOptions('language').map((language) => (
                      <option key={language} value={language}>{language}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Condition
                  <select
                    name="condition"
                    value={filters.condition}
                    onChange={handleFilterChange}
                  >
                    <option value="">All conditions</option>
                    {getFilterOptions('condition').map((condition) => (
                      <option key={condition} value={condition}>{condition}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Availability
                  <select
                    name="availability"
                    value={filters.availability}
                    onChange={handleFilterChange}
                  >
                    <option value="">All availability</option>
                    {getFilterOptions('availability').map((availability) => (
                      <option key={availability} value={availability}>{availability}</option>
                    ))}
                  </select>
                </label>
              </div>
              <button
                className="clear-filters-button"
                type="button"
                onClick={clearFilters}
                disabled={!hasActiveFilters}
              >
                Clear filters
              </button>
            </div>
          )}

          {isLoading ? (
            <p className="catalog-note" role="status">Loading books...</p>
          ) : catalogError ? (
            <div className="catalog-error" role="alert">
              <p>{catalogError}</p>
              <button className="secondary-button" type="button" onClick={loadBooks}>
                Try again
              </button>
            </div>
          ) : books.length === 0 ? (
            <p className="empty-state">No books yet. Add the first one to get started.</p>
          ) : matchingBooks.length === 0 ? (
            <p className="empty-state">
              No books match your search or filters. Try changing them or clear all filters.
            </p>
          ) : (
            <ul className="book-list">
              {matchingBooks.map((book) => (
                <li className="book-card" key={book._id}>
                  <div className="book-card-heading">
                    <div>
                      <h3>{book.title}</h3>
                      <p className="book-author">by {book.author}</p>
                    </div>
                    <span
                      className={`availability ${
                        book.availability === 'Available' ? 'available' : 'borrowed'
                      }`}
                    >
                      {book.availability}
                    </span>
                  </div>
                  <p className="book-details">
                    {book.genre} <span aria-hidden="true">·</span> {book.language}
                  </p>
                  <p className="book-details">
                    ISBN {book.isbn} <span aria-hidden="true">·</span> {book.condition}
                  </p>
                  {book.description && <p className="book-description">{book.description}</p>}
                  {book.availability === 'Available' && (
                    <div className="borrow-request">
                      {activeRequestBookId === book._id ? (
                        <form onSubmit={(event) => handleBorrowRequest(event, book)}>
                          <div className="request-actions">
                            <button type="submit" disabled={isSubmittingRequest}>
                              {isSubmittingRequest ? 'Submitting...' : 'Submit request'}
                            </button>
                            <button
                              className="clear-filters-button"
                              type="button"
                              onClick={closeBorrowRequest}
                              disabled={isSubmittingRequest}
                            >
                              Cancel
                            </button>
                          </div>
                        </form>
                      ) : (
                        <button
                          type="button"
                          onClick={() => openBorrowRequest(book._id)}
                        >
                          Request to Borrow
                        </button>
                      )}
                    </div>
                  )}
                  {requestMessage.bookId === book._id && requestMessage.text && (
                    <p
                      className={`form-message ${requestMessage.type}`}
                      role={requestMessage.type === 'error' ? 'alert' : 'status'}
                    >
                      {requestMessage.text}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="my-requests panel" aria-labelledby="my-requests-heading">
        <div className="section-heading admin-heading">
          <div>
            <p className="eyebrow">Your account</p>
            <h2 id="my-requests-heading">My Borrowing History</h2>
          </div>
          <button
            className="clear-filters-button"
            type="button"
            onClick={loadMyBorrowingHistory}
            disabled={isLoadingRequests}
          >
            {isLoadingRequests ? 'Refreshing...' : 'Refresh history'}
          </button>
        </div>
        <p className="request-lookup-note">
          These requests are linked to your signed-in account ({authUser.name}).
        </p>
        {requestHistoryError && (
          <div className="catalog-error" role="alert">
            <p>{requestHistoryError}</p>
            <button
              className="secondary-button"
              type="button"
              onClick={loadMyBorrowingHistory}
              disabled={isLoadingRequests}
            >
              Try again
            </button>
          </div>
        )}
        {isLoadingRequests && (
          <p className="catalog-note" role="status">Loading your borrowing history...</p>
        )}
        {!requestHistoryError && !isLoadingRequests && myBorrowRequests.length === 0 && (
          <p className="empty-state" role="status">
            You have no borrowing history yet. Your requests will appear here.
          </p>
        )}
        {myBorrowRequests.length > 0 && (
          <ul className="request-list" aria-live="polite">
            {myBorrowRequests.map((request) => (
              <li className="request-card" key={request._id}>
                <h3>{request.bookTitle}</h3>
                <p>
                  <strong>Request date:</strong>{' '}
                  <time dateTime={request.createdAt}>
                    {new Date(request.createdAt).toLocaleString()}
                  </time>
                </p>
                <p>
                  <strong>Request status:</strong>{' '}
                  <span
                    className={`request-status ${
                      request.status === 'Declined' ? 'rejected' : request.status.toLowerCase()
                    }`}
                  >
                    {request.status === 'Declined' ? 'Rejected' : request.status}
                  </span>
                </p>
                <p>
                  <strong>Book availability:</strong>{' '}
                  {request.book?.availability ? (
                    <span
                      className={`availability ${request.book.availability.toLowerCase()}`}
                    >
                      {request.book.availability}
                    </span>
                  ) : (
                    <span className="availability-unknown">Not available</span>
                  )}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      {isAdmin && (
      <section className="admin-requests panel" aria-labelledby="admin-requests-heading">
        <div className="section-heading admin-heading">
          <div>
            <p className="eyebrow">Demo management</p>
            <h2 id="admin-requests-heading">Admin: Manage Borrow Requests</h2>
          </div>
          <button
            className="clear-filters-button"
            type="button"
            onClick={loadAdminRequests}
            disabled={isLoadingAdminRequests}
          >
            {isLoadingAdminRequests ? 'Refreshing...' : 'Refresh requests'}
          </button>
        </div>
        <p className="request-lookup-note">
          Admin actions are restricted to accounts with the admin role. Approving marks
          the book as borrowed; marking it returned makes it available again. Availability
          repair is only offered when the book has not changed or been returned since
          that request was approved.
        </p>
        {adminRequestsMessage.text && (
          <p
            className={`form-message ${adminRequestsMessage.type}`}
            role={adminRequestsMessage.type === 'error' ? 'alert' : 'status'}
          >
            {adminRequestsMessage.text}
          </p>
        )}
        {isLoadingAdminRequests ? (
          <p className="catalog-note" role="status">Loading borrow requests...</p>
        ) : adminRequestsError ? (
          <div className="catalog-error" role="alert">
            <p>{adminRequestsError}</p>
            <button className="secondary-button" type="button" onClick={loadAdminRequests}>
              Try again
            </button>
          </div>
        ) : adminRequests.length === 0 ? (
          <p className="empty-state">There are no borrow requests to manage yet.</p>
        ) : (
          <ul className="request-list admin-request-list">
            {adminRequests.map((request) => (
              <li className="request-card" key={request._id}>
                <h3>{request.bookTitle}</h3>
                <p><strong>Requester:</strong> {request.requesterName}</p>
                <p>
                  <strong>Requested:</strong>{' '}
                  <time dateTime={request.createdAt}>
                    {new Date(request.createdAt).toLocaleString()}
                  </time>
                </p>
                <p>
                  <strong>Status:</strong>{' '}
                  <span className={`request-status ${request.status.toLowerCase()}`}>
                    {request.status}
                  </span>
                </p>
                {request.status === 'Pending' && (
                  <div className="admin-request-actions">
                    <button
                      type="button"
                      onClick={() => handleAdminRequestStatus(request, 'Approved')}
                      disabled={updatingRequestId === request._id}
                    >
                      {updatingRequestId === request._id ? 'Updating...' : 'Approve'}
                    </button>
                    <button
                      className="reject-button"
                      type="button"
                      onClick={() => handleAdminRequestStatus(request, 'Rejected')}
                      disabled={updatingRequestId === request._id}
                    >
                      {updatingRequestId === request._id ? 'Updating...' : 'Reject'}
                    </button>
                  </div>
                )}
                {canRepairAvailability(request) && (
                    <div className="admin-request-actions">
                      <button
                        type="button"
                        onClick={() => handleRepairAvailability(request)}
                        disabled={updatingRequestId === request._id}
                      >
                        {updatingRequestId === request._id
                          ? 'Updating...'
                          : 'Repair book availability'}
                      </button>
                    </div>
                  )}
              </li>
            ))}
          </ul>
        )}

        <div className="borrowed-books">
          <div className="section-heading">
            <p className="eyebrow">Manage returns</p>
            <h3>Borrowed Books</h3>
          </div>
          {isLoadingBorrowedBooks ? (
            <p className="catalog-note" role="status">Loading books...</p>
          ) : borrowedBooksError ? (
            <div className="catalog-error" role="alert">
              <p>{borrowedBooksError}</p>
              <button className="secondary-button" type="button" onClick={loadBorrowedBooks}>
                Try again
              </button>
            </div>
          ) : borrowedBooks.length === 0 ? (
            <p className="empty-state">There are no borrowed books to return.</p>
          ) : (
            <ul className="request-list">
              {borrowedBooks.map((book) => (
                  <li className="request-card" key={book._id}>
                    <h3>{book.title}</h3>
                    <p><strong>Author:</strong> {book.author}</p>
                    <p><strong>Availability:</strong> Borrowed</p>
                    <button
                      type="button"
                      onClick={() => handleMarkReturned(book)}
                      disabled={updatingRequestId === book._id}
                    >
                      {updatingRequestId === book._id ? 'Updating...' : 'Mark as Returned'}
                    </button>
                  </li>
              ))}
            </ul>
          )}
        </div>
      </section>
      )}
    </main>
  );
}

export default App;
