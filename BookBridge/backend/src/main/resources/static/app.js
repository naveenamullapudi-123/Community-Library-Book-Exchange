/* BookBridge's small client-side app uses the Spring REST API directly. */
(() => {
  "use strict";

  const API_BASE = "";
  const SESSION_KEY = "bookbridge.session";
  const main = document.querySelector("#main");
  const toastRegion = document.querySelector("#toast-region");
  const appState = { wishlistIds: new Set(), catalogBooks: [], currentProfile: null };
  let toastTimer;

  const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
  const safeImageUrl = (value) => {
    try {
      const url = new URL(value, location.origin);
      return ["http:", "https:"].includes(url.protocol) ? url.href : "";
    } catch {
      return "";
    }
  };
  const session = () => {
    try {
      const value = JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null");
      if (value?.expiresAt && Date.parse(value.expiresAt) <= Date.now()) {
        sessionStorage.removeItem(SESSION_KEY);
        return null;
      }
      return value;
    } catch {
      sessionStorage.removeItem(SESSION_KEY);
      return null;
    }
  };
  const requireSession = () => {
    if (session()?.token) return true;
    location.hash = "#/login";
    showToast("Sign in to use that part of your library.", true);
    return false;
  };
  const queryString = (values) => {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
      if (value !== "" && value !== null && value !== undefined) params.set(key, value);
    });
    const query = params.toString();
    return query ? `?${query}` : "";
  };
  async function api(path, options = {}) {
    const current = session();
    const headers = new Headers(options.headers || {});
    if (current?.token) headers.set("Authorization", `Bearer ${current.token}`);
    if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    let response;
    try {
      response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    } catch {
      throw new Error("Cannot reach BookBridge. Check that the backend is running at http://localhost:8080.");
    }
    const text = response.status === 204 ? "" : await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }
    if (!response.ok) {
      if (response.status === 401 && current?.token && !path.startsWith("/api/auth/")) {
        sessionStorage.removeItem(SESSION_KEY);
        updateNavigation();
        location.hash = "#/login";
      }
      const validation = Array.isArray(data?.errors)
        ? data.errors.map((error) => typeof error === "string" ? error : `${error.field}: ${error.message}`).join(" ")
        : "";
      throw new Error(data?.detail || validation || data?.title || `Request failed (${response.status}).`);
    }
    return data;
  }
  function showToast(message, isError = false) {
    clearTimeout(toastTimer);
    toastRegion.innerHTML = `<div class="toast${isError ? " error" : ""}" role="${isError ? "alert" : "status"}">${escapeHtml(message)}</div>`;
    toastTimer = setTimeout(() => { toastRegion.innerHTML = ""; }, 4800);
  }
  function setMain(markup) {
    main.innerHTML = markup;
    main.focus({ preventScroll: true });
  }
  function loading(message = "Loading…") {
    setMain(`<div class="loading-state"><span class="spinner" aria-hidden="true"></span>${escapeHtml(message)}</div>`);
  }
  function errorPage(error, retryHash = location.hash) {
    setMain(`<div class="error-panel"><strong>Something went wrong.</strong><p>${escapeHtml(error.message || error)}</p><button class="button button-outline button-small" data-action="retry" data-hash="${escapeHtml(retryHash)}">Try again</button></div>`);
  }
  function dateText(value) {
    if (!value) return "Not set";
    const date = new Date(value.length === 10 ? `${value}T12:00:00` : value);
    return Number.isNaN(date.getTime()) ? escapeHtml(value) : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date);
  }
  function localDateTimeValue(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }
  function statusBadge(status) {
    const label = String(status || "UNKNOWN").replaceAll("_", " ").toLowerCase();
    return `<span class="badge ${escapeHtml(String(status || "").toLowerCase())}">${escapeHtml(label)}</span>`;
  }
  function cover(book, extraClass = "") {
    const image = safeImageUrl(book.coverImageUrl);
    const fallback = `<div class="cover-placeholder" aria-hidden="true">${escapeHtml(book.title || "A good read")}</div>`;
    return `<div class="book-cover ${extraClass}">${image
      ? `<img data-cover-title="${escapeHtml(book.title || "A good read")}" src="${escapeHtml(image)}" alt="Cover of ${escapeHtml(book.title)}" loading="lazy">`
      : fallback}</div>`;
  }
  function updateNavigation() {
    const current = session();
    document.querySelectorAll(".signed-in-nav").forEach((element) => { element.hidden = !current; });
    document.querySelectorAll(".auth-nav").forEach((element) => { element.hidden = Boolean(current); });
    document.querySelector("#logout-button").hidden = !current;
    document.querySelector("#signed-in-user").hidden = !current;
    document.querySelector("#topbar-user").hidden = !current;
    document.querySelectorAll(".mobile-signed-in").forEach((element) => { element.hidden = !current; });
    document.querySelectorAll(".mobile-signed-out").forEach((element) => { element.hidden = Boolean(current); });
    if (current) {
      const name = current.user?.displayName || "Reader";
      document.querySelector("#signed-in-user").innerHTML = `${escapeHtml(name)}<small>Community reader</small>`;
      document.querySelector("#topbar-user").textContent = name;
      refreshNotificationCount();
    } else {
      document.querySelector("#notification-count").hidden = true;
      document.querySelector("#mobile-notification-count").hidden = true;
    }
    updateActiveNavigation();
  }
  function updateActiveNavigation() {
    const route = location.hash.replace(/^#\/?/, "").split(/[/?]/)[0] || "catalog";
    document.querySelectorAll("[data-nav]").forEach((link) => {
      link.classList.toggle("active", link.dataset.nav === route);
    });
  }
  async function refreshNotificationCount() {
    if (!session()?.token) return;
    try {
      const unread = await api("/api/notifications/unread");
      const count = unread.length;
      const badge = document.querySelector("#notification-count");
      badge.textContent = count > 99 ? "99+" : String(count);
      badge.hidden = count === 0;
      document.querySelector("#mobile-notification-count").hidden = count === 0;
    } catch {
      // Page-level API requests provide the visible error when a reader opens notifications.
    }
  }
  function bookCard(book) {
    const saved = appState.wishlistIds.has(book.id);
    const canWishlist = session() && session().user?.id !== book.owner?.id;
    return `<article class="card book-card">
      <div class="book-cover">
        ${safeImageUrl(book.coverImageUrl)
          ? `<img data-cover-title="${escapeHtml(book.title || "A good read")}" src="${escapeHtml(safeImageUrl(book.coverImageUrl))}" alt="Cover of ${escapeHtml(book.title)}" loading="lazy">`
          : `<div class="cover-placeholder" aria-hidden="true">${escapeHtml(book.title)}</div>`}
        ${canWishlist ? `<button class="wishlist-toggle${saved ? " saved" : ""}" type="button" data-action="toggle-wishlist" data-id="${escapeHtml(book.id)}" aria-label="${saved ? "Remove from" : "Add to"} wishlist" title="${saved ? "Remove from wishlist" : "Save to wishlist"}">${saved ? "♥" : "♡"}</button>` : ""}
      </div>
      <div class="book-card-body">
        ${statusBadge(book.status)}
        <h3>${escapeHtml(book.title)}</h3>
        <p class="book-author">by ${escapeHtml(book.author)}</p>
        <div class="book-meta">${book.genre ? `<span class="badge">${escapeHtml(book.genre)}</span>` : ""}${book.condition ? `<span class="badge">${escapeHtml(book.condition.replaceAll("_", " ").toLowerCase())}</span>` : ""}</div>
        <div class="book-card-actions">
          <span class="small-meta">${escapeHtml(book.owner?.displayName || "Community member")}${book.owner?.cityOrPostalCode ? ` · ${escapeHtml(book.owner.cityOrPostalCode)}` : ""}</span>
          <a class="button button-small button-outline" href="#/book/${encodeURIComponent(book.id)}">Book details</a>
        </div>
      </div>
    </article>`;
  }
  function emptyState(title, message, action = "") {
    return `<div class="empty-state"><strong>${escapeHtml(title)}</strong>${escapeHtml(message)}${action}</div>`;
  }
  function pageHeading(eyebrow, title, description, action = "") {
    return `<div class="page-heading"><div><p class="eyebrow">${escapeHtml(eyebrow)}</p><h1>${escapeHtml(title)}</h1><p class="page-intro">${escapeHtml(description)}</p></div>${action}</div>`;
  }

  async function renderRoute() {
    updateNavigation();
    const hash = location.hash || "#/catalog";
    const routeString = hash.replace(/^#\/?/, "");
    const [routePart, ...queryParts] = routeString.split("?");
    const segments = routePart.split("/").filter(Boolean).map(decodeURIComponent);
    const params = new URLSearchParams(queryParts.join("?"));
    const route = segments[0] || "catalog";
    try {
      switch (route) {
        case "": case "catalog": await renderCatalog(); break;
        case "dashboard": await renderDashboard(); break;
        case "login": renderAuth("login"); break;
        case "register": renderAuth("register"); break;
        case "book": if (segments[1]) await renderBook(segments[1]); else await renderCatalog(); break;
        case "bookshelf": await renderBookshelf(); break;
        case "loans": await renderLoans(); break;
        case "wishlist": await renderWishlist(); break;
        case "profile": await renderProfile(segments[1] || session()?.user?.id); break;
        case "forum": await renderForum(); break;
        case "messages": await renderMessages(params.get("with") || ""); break;
        case "notifications": await renderNotifications(); break;
        case "more": renderMore(); break;
        case "new-book": renderBookForm(); break;
        case "edit-book": if (segments[1]) await renderBookFormById(segments[1]); else await renderCatalog(); break;
        default: await renderCatalog();
      }
    } catch (error) {
      errorPage(error);
    }
    updateActiveNavigation();
  }
  function renderAuth(mode) {
    const registering = mode === "register";
    setMain(`<div class="auth-wrap"><section class="card auth-card">
      <a class="brand" href="#/catalog"><span class="brand-mark">B</span><span>Book<span class="brand-light">Bridge</span><small>COMMUNITY LIBRARY</small></span></a>
      <p class="eyebrow">${registering ? "JOIN THE COMMUNITY" : "WELCOME BACK"}</p>
      <h1>${registering ? "Make room for a new story." : "Good to see you again."}</h1>
      <p class="page-intro">${registering ? "Create an account to share books and meet readers nearby." : "Sign in to pick up where your next good read begins."}</p>
      <form data-form="${registering ? "register" : "login"}">
        ${registering ? `<div class="field"><label for="auth-name">Your name</label><input id="auth-name" name="displayName" autocomplete="name" maxlength="80" required></div>` : ""}
        <div class="field"><label for="auth-email">Email address</label><input id="auth-email" name="email" type="email" autocomplete="email" maxlength="254" required></div>
        <div class="field"><label for="auth-password">Password</label><input id="auth-password" name="password" type="password" autocomplete="${registering ? "new-password" : "current-password"}" minlength="${registering ? "8" : "1"}" maxlength="72" required>${registering ? `<small class="field-hint">Use at least 8 characters.</small>` : ""}</div>
        ${registering ? `<div class="field"><label for="auth-location">City or postal code <span class="field-hint">(optional)</span></label><input id="auth-location" name="cityOrPostalCode" autocomplete="address-level2" maxlength="120"></div>` : ""}
        <button class="button" type="submit">${registering ? "Create my account" : "Sign in"}</button>
      </form>
      <p class="auth-switch">${registering ? "Already have an account?" : "New to BookBridge?"} <a href="#/${registering ? "login" : "register"}">${registering ? "Sign in" : "Create an account"}</a></p>
    </section></div>`);
  }
  async function renderDashboard() {
    if (!session()) {
      setMain(`${pageHeading("A COMMUNITY OF READERS", "Stories are better when shared.", "Find your next read, pass a favorite along, and meet the readers in your neighborhood.", `<a class="button" href="#/catalog">Explore the catalog <span aria-hidden="true">→</span></a>`)}
        <section class="card panel"><div class="section-title"><h2>Start with a good book</h2></div><p class="page-intro">Browse books shared by your community. Create a free account to borrow, list books, save favorites, and join the conversation.</p><div class="button-row"><a class="button" href="#/catalog">Browse community books</a><a class="button button-outline" href="#/register">Join BookBridge</a></div></section>`);
      return;
    }
    loading("Gathering your library…");
    const [shelf, requests, borrowed, lent, unread] = await Promise.all([
      api("/api/books/me/bookshelf"), api("/api/borrow-requests/mine"),
      api("/api/loans/borrowed"), api("/api/loans/lent"), api("/api/notifications/unread")
    ]);
    setMain(`${pageHeading("YOUR COMMUNITY LIBRARY", `Welcome back, ${session().user?.displayName || "reader"}.`, "Here's what's happening with your books and reading community.", `<a class="button" href="#/new-book">＋ List a book</a>`)}
      <div class="grid stats-grid">
        <article class="card stat-card"><p class="stat-label">Books listed</p><p class="stat-number">${shelf.books.length}</p><p class="stat-caption">On your bookshelf</p></article>
        <article class="card stat-card"><p class="stat-label">Requests to review</p><p class="stat-number">${shelf.incomingRequests.length}</p><p class="stat-caption">Waiting for your response</p></article>
        <article class="card stat-card"><p class="stat-label">Borrowing</p><p class="stat-number">${borrowed.length}</p><p class="stat-caption">Active books borrowed</p></article>
        <article class="card stat-card"><p class="stat-label">Unread updates</p><p class="stat-number">${unread.length}</p><p class="stat-caption">Community notifications</p></article>
      </div>
      <div class="grid split-grid">
        <section class="card panel"><div class="section-title"><h2>Incoming requests</h2><a href="#/bookshelf">View bookshelf</a></div>
          ${shelf.incomingRequests.length ? shelf.incomingRequests.slice(0, 3).map((item) => `<article class="list-card"><div class="list-card-header"><div><h3>${escapeHtml(item.bookTitle)}</h3><p>${escapeHtml(item.borrower?.displayName)} would like to borrow this book.</p></div>${statusBadge(item.status)}</div></article>`).join("") : emptyState("All caught up", "New borrow requests will appear here.")}</section>
        <section class="card panel"><div class="section-title"><h2>Due dates</h2><a href="#/loans">My loans</a></div>
          ${borrowed.length ? borrowed.slice(0, 4).map((loan) => `<article class="list-card"><div class="list-card-header"><div><h3>${escapeHtml(loan.bookTitle)}</h3><p>Borrowed from ${escapeHtml(loan.ownerName)}</p></div>${statusBadge(loan.status)}</div><p class="small-meta">Due ${dateText(loan.dueDate)}</p></article>`).join("") : emptyState("Nothing due yet", "Your active borrowed books will show up here.")}</section>
      </div>`);
  }
  async function renderCatalog(filters = null) {
    const params = filters || new URLSearchParams(location.hash.split("?")[1] || "");
    const search = params.get("q") || "";
    const status = params.get("status") || "AVAILABLE";
    const language = params.get("language") || "";
    const condition = params.get("condition") || "";
    const genre = params.get("genre") || "";
    const [books, genres] = await Promise.all([
      status === "ALL"
        ? Promise.all(["AVAILABLE", "LENT_OUT", "UNAVAILABLE"].map((value) =>
          api(`/api/books${queryString({ q: search, status: value, language, condition })}`)))
          .then((groups) => groups.flat())
        : api(`/api/books${queryString({ q: search, status, language, condition })}`),
      api("/api/books/genres")
    ]);
    const filteredBooks = books.filter((book) =>
      !genre || book.genre?.toLowerCase() === genre.toLowerCase());
    appState.catalogBooks = filteredBooks;
    if (session()) {
      const wishlist = await api("/api/wishlist");
      appState.wishlistIds = new Set((wishlist.books || []).map((book) => book.id));
    } else {
      appState.wishlistIds.clear();
    }
    setMain(`${pageHeading("THE COMMUNITY SHELF", "Find a book to love.", "Every book here has been shared by a reader. Search, explore a genre, and see where a story takes you.", session() ? `<a class="button button-outline" href="#/new-book">＋ List a book</a>` : `<a class="button button-outline" href="#/register">Join to share a book</a>`)}
      <form class="card catalog-toolbar" id="catalog-filters" data-form="catalog-filters">
        <div class="field search-field"><label for="catalog-search">Search books</label><input class="toolbar-input" id="catalog-search" name="q" value="${escapeHtml(search)}" placeholder="Title, author, or genre"></div>
        <div class="field"><label for="filter-genre">Genre</label><select id="filter-genre" name="genre"><option value="">All genres</option>${genres.map((value) => `<option value="${escapeHtml(value)}"${genre === value ? " selected" : ""}>${escapeHtml(value)}</option>`).join("")}</select></div>
        <div class="field"><label for="filter-status">Availability</label><select id="filter-status" name="status"><option value="AVAILABLE"${status === "AVAILABLE" ? " selected" : ""}>Available</option><option value="LENT_OUT"${status === "LENT_OUT" ? " selected" : ""}>Lent out</option><option value="UNAVAILABLE"${status === "UNAVAILABLE" ? " selected" : ""}>Unavailable</option><option value="ALL"${status === "ALL" ? " selected" : ""}>All statuses</option></select></div>
        <div class="field"><label for="filter-language">Language</label><input class="toolbar-input" id="filter-language" name="language" value="${escapeHtml(language)}" placeholder="Any language"></div>
        <div class="field"><label for="filter-condition">Condition</label><select id="filter-condition" name="condition"><option value="">Any condition</option>${conditionOptions(condition)}</select></div>
        <button class="button button-small" type="submit">Search</button>
      </form>
      <p class="filters-hint">Showing ${filteredBooks.length} book${filteredBooks.length === 1 ? "" : "s"}. Distance filtering is not available yet.</p>
      ${filteredBooks.length ? `<div class="grid book-grid">${filteredBooks.map(bookCard).join("")}</div>` : emptyState("No books found", "Try another search or change your filters.")}`);
  }
  function conditionOptions(selected = "") {
    return [["EXCELLENT", "Excellent"], ["GOOD", "Good"], ["ACCEPTABLE", "Acceptable"], ["WORN_BUT_READABLE", "Worn but readable"]]
      .map(([value, label]) => `<option value="${value}"${selected === value ? " selected" : ""}>${label}</option>`).join("");
  }
  function bookFormMarkup(book = null) {
    const editing = Boolean(book);
    const field = (name, label, value = "", options = {}) => {
      const id = `book-${name}`;
      const currentValue = value ?? "";
      if (options.select) {
        return `<div class="field"><label for="${id}">${label}</label><select id="${id}" name="${name}"${options.required ? " required" : ""}>${options.select}</select></div>`;
      }
      if (options.textarea) {
        return `<div class="field field-wide"><label for="${id}">${label}</label><textarea id="${id}" name="${name}" maxlength="${options.max || 5000}">${escapeHtml(currentValue)}</textarea></div>`;
      }
      return `<div class="field${options.wide ? " field-wide" : ""}"><label for="${id}">${label}</label><input id="${id}" name="${name}" value="${escapeHtml(currentValue)}" ${options.type ? `type="${options.type}"` : "type=\"text\""}${options.max ? ` maxlength="${options.max}"` : ""}${options.min ? ` min="${options.min}"` : ""}${options.maxValue ? ` max="${options.maxValue}"` : ""}${options.required ? " required" : ""}></div>`;
    };
    const condChoices = [["EXCELLENT", "Excellent"], ["GOOD", "Good"], ["ACCEPTABLE", "Acceptable"], ["WORN_BUT_READABLE", "Worn but readable"]]
      .map(([value, label]) => `<option value="${value}"${(book?.condition || "GOOD") === value ? " selected" : ""}>${label}</option>`).join("");
    const statusChoices = [["AVAILABLE", "Available"], ["LENT_OUT", "Lent out"], ["UNAVAILABLE", "Unavailable"]]
      .map(([value, label]) => `<option value="${value}"${(book?.status || "AVAILABLE") === value ? " selected" : ""}>${label}</option>`).join("");
    return `<section class="card form-card">
      <form data-form="book" data-id="${escapeHtml(book?.id || "")}">
        ${field("isbn", "ISBN", book?.isbn, { max: 20, required: true })}
        <div class="field"><span class="field-label">Book details</span><button class="button button-outline button-small" type="button" data-action="isbn-lookup">Look up ISBN</button><small class="field-hint">Open Library lookup is optional. You can enter details manually.</small></div>
        ${field("title", "Title", book?.title, { max: 200, required: true })}
        ${field("author", "Author", book?.author, { max: 160, required: true })}
        ${field("genre", "Genre", book?.genre, { max: 80 })}
        ${field("language", "Language", book?.language, { max: 40 })}
        ${field("publisher", "Publisher", book?.publisher, { max: 160 })}
        ${field("condition", "Condition", "", { select: condChoices, required: true })}
        ${field("status", "Availability", "", { select: statusChoices })}
        ${field("loanDurationDays", "Loan duration (days)", book?.loanDurationDays || 14, { type: "number", min: 1, maxValue: 365 })}
        ${field("cityOrPostalCode", "City or postal code", book?.owner?.cityOrPostalCode || "", { max: 120 })}
        ${field("pickupPreferences", "Pickup preferences", book?.pickupPreferences, { max: 500 })}
        ${field("coverImageUrl", "Cover image URL", book?.coverImageUrl, { max: 1000, wide: true, type: "url" })}
        ${field("description", "Description", book?.description, { textarea: true, max: 5000 })}
        <div class="button-row field-wide"><button class="button" type="submit">${editing ? "Save book changes" : "Add to my bookshelf"}</button><a class="button button-outline" href="${editing ? `#/book/${encodeURIComponent(book.id)}` : "#/bookshelf"}">Cancel</a></div>
      </form>
    </section>`;
  }
  function renderBookForm(book = null) {
    if (!requireSession()) return;
    setMain(`${pageHeading("YOUR BOOKSHELF", book ? "Edit your listing." : "Share a book.", book ? "Update the listing details or availability." : "Add a book to your community shelf. ISBN lookup can fill in details, or enter them yourself.")}${bookFormMarkup(book)}`);
  }
  async function renderBookFormById(id) {
    if (!requireSession()) return;
    loading("Loading your book…");
    const book = await api(`/api/books/${encodeURIComponent(id)}`);
    if (book.owner?.id !== session()?.user?.id) throw new Error("You can only edit a book you listed.");
    renderBookForm(book);
  }
  async function renderBook(bookId) {
    const book = await api(`/api/books/${encodeURIComponent(bookId)}`);
    appState.wishlistIds.clear();
    if (session()) {
      const wishlist = await api("/api/wishlist");
      appState.wishlistIds = new Set((wishlist.books || []).map((saved) => saved.id));
    }
    const isOwner = session()?.user?.id === book.owner?.id;
    const saved = appState.wishlistIds.has(book.id);
    const authActions = session() && !isOwner
      ? `<div class="button-row"><button class="button button-outline" type="button" data-action="toggle-wishlist" data-id="${escapeHtml(book.id)}">${saved ? "♥ Saved to wishlist" : "♡ Save to wishlist"}</button><button class="button" type="button" data-action="borrow-form" data-id="${escapeHtml(book.id)}"${book.status !== "AVAILABLE" ? " disabled" : ""}>Request this book</button><button class="button button-quiet" type="button" data-action="message-user" data-id="${escapeHtml(book.owner?.id)}">Message owner</button></div><div id="borrow-form-slot"></div>`
      : isOwner ? `<div class="button-row"><a class="button" href="#/edit-book/${encodeURIComponent(book.id)}">Edit listing</a><button class="button button-outline" type="button" data-action="change-book-status" data-id="${escapeHtml(book.id)}" data-status="${book.status === "AVAILABLE" ? "UNAVAILABLE" : "AVAILABLE"}">${book.status === "AVAILABLE" ? "Mark unavailable" : "Mark available"}</button><button class="button button-danger" type="button" data-action="delete-book" data-id="${escapeHtml(book.id)}">Delete listing</button></div>`
      : `<div class="button-row"><a class="button" href="#/login">Sign in to borrow</a></div>`;
    setMain(`<a href="#/catalog" class="back-link">← Back to catalog</a><div class="details-layout" style="margin-top:20px">
      <div>${cover(book, "details-cover")}</div>
      <div class="details-body"><p class="eyebrow">COMMUNITY BOOK</p>${statusBadge(book.status)}<h1>${escapeHtml(book.title)}</h1><p class="details-author">by ${escapeHtml(book.author)}</p>
        <p class="details-description">${escapeHtml(book.description || "The owner has not added a description yet.")}</p>
        <dl class="details-data"><div><dt>Condition</dt><dd>${escapeHtml((book.condition || "").replaceAll("_", " ").toLowerCase())}</dd></div><div><dt>Genre</dt><dd>${escapeHtml(book.genre || "Not specified")}</dd></div><div><dt>Language</dt><dd>${escapeHtml(book.language || "Not specified")}</dd></div><div><dt>Loan duration</dt><dd>${escapeHtml(book.loanDurationDays || 14)} days</dd></div><div><dt>Pickup</dt><dd>${escapeHtml(book.pickupPreferences || "Ask the owner")}</dd></div><div><dt>Listed by</dt><dd><a href="#/profile/${encodeURIComponent(book.owner?.id || "")}">${escapeHtml(book.owner?.displayName || "Community member")}</a>${book.owner?.cityOrPostalCode ? ` · ${escapeHtml(book.owner.cityOrPostalCode)}` : ""}</dd></div></dl>
        ${authActions}
      </div></div>`);
  }
  function bookshelfBook(book) {
    return `<article class="list-card"><div class="list-card-header"><div><h3><a href="#/book/${encodeURIComponent(book.id)}">${escapeHtml(book.title)}</a></h3><p>by ${escapeHtml(book.author)} · ${escapeHtml(book.isbn)}</p></div>${statusBadge(book.status)}</div>
      <div class="button-row"><a class="button button-small button-outline" href="#/edit-book/${encodeURIComponent(book.id)}">Edit</a><a class="button button-small button-quiet" href="#/book/${encodeURIComponent(book.id)}">Details</a><button class="button button-small button-danger" data-action="delete-book" data-id="${escapeHtml(book.id)}" type="button">Delete</button></div></article>`;
  }
  function incomingRequestCard(request) {
    return `<article class="list-card"><div class="list-card-header"><div><h3>${escapeHtml(request.bookTitle)}</h3><p>From <a href="#/profile/${encodeURIComponent(request.borrower?.id || "")}">${escapeHtml(request.borrower?.displayName)}</a>${request.borrower?.cityOrPostalCode ? ` · ${escapeHtml(request.borrower.cityOrPostalCode)}` : ""}</p></div>${statusBadge(request.status)}</div>
      <p>Requested start: ${dateText(request.desiredStartDate)} · ${escapeHtml(request.loanDurationDays)} days</p>${request.personalMessage ? `<p>“${escapeHtml(request.personalMessage)}”</p>` : ""}
      ${request.status === "PENDING" ? `<form data-form="borrow-decision" data-id="${escapeHtml(request.id)}" class="form-grid">
        <div class="field"><label for="exchange-${escapeHtml(request.id)}">Exchange method</label><select id="exchange-${escapeHtml(request.id)}" name="exchangeMethod"><option value="IN_PERSON">In person</option><option value="POSTAL_OR_COURIER">Postal / courier</option></select></div>
        <div class="field"><label for="handover-date-${escapeHtml(request.id)}">Handover date</label><input id="handover-date-${escapeHtml(request.id)}" name="handoverDate" type="date" min="${escapeHtml(request.desiredStartDate)}" value="${escapeHtml(request.desiredStartDate)}"></div>
        <div class="field"><label for="location-${escapeHtml(request.id)}">Agreed location</label><input id="location-${escapeHtml(request.id)}" name="handoverLocation" maxlength="300" placeholder="Pickup point or postal arrangement"></div>
        <div class="field"><label for="response-${escapeHtml(request.id)}">Reply (optional)</label><input id="response-${escapeHtml(request.id)}" name="responseMessage" maxlength="1000"></div>
        <div class="button-row field-wide"><button class="button button-small" name="decision" value="APPROVE" type="submit">Approve request</button><button class="button button-small button-danger" name="decision" value="REJECT" type="submit">Decline</button></div>
      </form>` : ""}</article>`;
  }
  async function renderBookshelf() {
    if (!requireSession()) return;
    loading("Opening your bookshelf…");
    const [shelf, mine] = await Promise.all([api("/api/books/me/bookshelf"), api("/api/borrow-requests/mine")]);
    setMain(`${pageHeading("YOUR BOOKSHELF", "Your books, in one place.", "Manage your listings and take care of incoming requests.", `<a class="button" href="#/new-book">＋ List a book</a>`)}
      <section class="card panel" style="margin-bottom:18px"><div class="section-title"><h2>Upload a book list</h2></div><p class="page-intro">Import a CSV with ISBN, title, author, and condition columns. The backend validates the whole file before saving.</p><div class="field"><label for="csv-upload">Choose a CSV file (up to 2 MB)</label><input id="csv-upload" name="file" type="file" accept=".csv,text/csv"><small class="field-hint">Optional columns include genre, language, description, publisher, coverImageUrl, status, loanDurationDays, pickupPreferences, and cityOrPostalCode.</small></div></section>
      <div class="grid split-grid">
        <section class="card panel"><div class="section-title"><h2>Books you listed <span class="badge">${shelf.books.length}</span></h2></div>
          ${shelf.books.length ? shelf.books.map(bookshelfBook).join("") : emptyState("Your shelf is waiting", "Add a book to share it with your community.", `<a class="button button-small" href="#/new-book">List your first book</a>`)}</section>
        <div class="content-stack">
          <section class="card panel"><div class="section-title"><h2>Incoming requests <span class="badge">${shelf.incomingRequests.length}</span></h2></div>
            ${shelf.incomingRequests.length ? shelf.incomingRequests.map(incomingRequestCard).join("") : emptyState("No requests yet", "Borrow requests for your books will appear here.")}</section>
          <section class="card panel"><div class="section-title"><h2>Your requests</h2></div>
            ${mine.length ? mine.map((request) => `<article class="list-card"><div class="list-card-header"><div><h3><a href="#/book/${encodeURIComponent(request.bookId)}">${escapeHtml(request.bookTitle)}</a></h3><p>Request sent ${dateText(request.createdAt)}</p></div>${statusBadge(request.status)}</div>${request.responseMessage ? `<p>${escapeHtml(request.responseMessage)}</p>` : ""}</article>`).join("") : emptyState("No requests sent", "When you request a book, its status will show here.")}</section>
        </div>
      </div>`);
  }
  function loanCard(loan, side, reviewSubmitted = false) {
    const canReview = loan.status === "RETURNED";
    const otherId = side === "borrowed" ? loan.ownerId : loan.borrowerId;
    const otherName = side === "borrowed" ? loan.ownerName : loan.borrowerName;
    return `<article class="list-card"><div class="list-card-header"><div><h3><a href="#/book/${encodeURIComponent(loan.bookId)}">${escapeHtml(loan.bookTitle)}</a></h3><p>${side === "borrowed" ? "Owner" : "Borrower"}: <a href="#/profile/${encodeURIComponent(otherId)}">${escapeHtml(otherName)}</a></p></div>${statusBadge(loan.status)}</div>
      <p>Due ${dateText(loan.dueDate)} · Handover ${dateText(loan.handoverDate)} · ${escapeHtml(String(loan.exchangeMethod || "").replaceAll("_", " ").toLowerCase())}</p><p class="small-meta">${escapeHtml(loan.handoverLocation || "")}</p>
      ${loan.status === "ACTIVE" ? `<div class="button-row"><button class="button button-small button-outline" type="button" data-action="return-loan" data-id="${escapeHtml(loan.id)}">Mark returned</button>${side === "borrowed" ? `<button class="button button-small button-quiet" type="button" data-action="show-extension" data-id="${escapeHtml(loan.id)}">Request extension</button>` : ""}</div><div id="extension-slot-${escapeHtml(loan.id)}"></div>` : ""}
      ${canReview ? reviewSubmitted
        ? `<div class="success-panel review-submitted"><strong>Review submitted.</strong> You have reviewed ${escapeHtml(otherName)}. <a href="#/profile/${encodeURIComponent(otherId)}">View updated profile</a></div>`
        : `<form class="review-form" data-form="review" data-id="${escapeHtml(loan.id)}" data-reviewed-user-id="${escapeHtml(otherId)}"><p class="field-label">Review ${escapeHtml(otherName)}</p><div class="form-grid"><div class="field"><label for="rel-${escapeHtml(loan.id)}">Reliability</label><select id="rel-${escapeHtml(loan.id)}" name="reliability">${ratingOptions()}</select></div><div class="field"><label for="comm-${escapeHtml(loan.id)}">Communication</label><select id="comm-${escapeHtml(loan.id)}" name="communication">${ratingOptions()}</select></div><div class="field"><label for="care-${escapeHtml(loan.id)}">Care of books</label><select id="care-${escapeHtml(loan.id)}" name="careOfBooks">${ratingOptions()}</select></div><div class="field"><label for="comment-${escapeHtml(loan.id)}">Comment (optional)</label><input id="comment-${escapeHtml(loan.id)}" name="comment" maxlength="2000"></div></div><button class="button button-small" type="submit">Send review</button></form>` : ""}
    </article>`;
  }
  function ratingOptions() {
    return [5, 4, 3, 2, 1].map((rating) => `<option value="${rating}">${rating} — ${rating === 5 ? "Excellent" : rating === 1 ? "Needs improvement" : "Good"}</option>`).join("");
  }
  function extensionCard(item) {
    return `<article class="list-card"><div class="list-card-header"><div><h3>${escapeHtml(item.bookTitle)}</h3><p>From ${escapeHtml(item.borrowerName)} · asks for ${escapeHtml(item.additionalDays)} more days</p></div>${statusBadge(item.status)}</div>${item.message ? `<p>“${escapeHtml(item.message)}”</p>` : ""}
      ${item.status === "PENDING" ? `<form data-form="extension-decision" data-id="${escapeHtml(item.id)}" class="inline-form"><div class="field"><label for="extension-response-${escapeHtml(item.id)}">Reply (optional)</label><input id="extension-response-${escapeHtml(item.id)}" name="responseMessage" maxlength="1000"></div><button class="button button-small" name="decision" value="APPROVE" type="submit">Approve</button><button class="button button-small button-danger" name="decision" value="REJECT" type="submit">Decline</button></form>` : ""}</article>`;
  }
  async function renderLoans() {
    if (!requireSession()) return;
    loading("Checking your loans…");
    const [borrowed, lent, returned, incomingExtensions] = await Promise.all([
      api("/api/loans/borrowed"), api("/api/loans/lent"),
      api("/api/loans/returned"), api("/api/loans/extensions/incoming")
    ]);
    const currentUserId = session().user.id;
    const completed = await Promise.all(returned.map(async (loan) => {
      const reviews = await api(`/api/loans/${encodeURIComponent(loan.id)}/reviews`);
      return {
        loan,
        side: loan.borrowerId === currentUserId ? "borrowed" : "lent",
        reviewSubmitted: reviews.some((review) => review.reviewerId === currentUserId)
      };
    }));
    setMain(`${pageHeading("BORROWING & LENDING", "Keep every exchange on track.", "See borrowed books, books you've lent, due dates, and extension requests.")}
      <div class="grid split-grid">
        <section class="card panel"><div class="section-title"><h2>Books I've borrowed <span class="badge">${borrowed.length}</span></h2></div>
          ${borrowed.length ? borrowed.map((loan) => loanCard(loan, "borrowed")).join("") : emptyState("No active borrowing", "Books you borrow and their due dates will show here.")}</section>
        <div class="content-stack">
          <section class="card panel"><div class="section-title"><h2>Books I've lent <span class="badge">${lent.length}</span></h2></div>
            ${lent.length ? lent.map((loan) => loanCard(loan, "lent")).join("") : emptyState("Nothing lent out", "Your active lending exchanges will show here.")}</section>
          <section class="card panel"><div class="section-title"><h2>Extension requests <span class="badge">${incomingExtensions.length}</span></h2></div>
            ${incomingExtensions.length ? incomingExtensions.map(extensionCard).join("") : emptyState("No extensions waiting", "Requests from borrowers will appear here.")}</section>
        </div>
      </div>`);
    const completedSection = `<section class="card panel completed-exchanges"><div class="section-title"><h2>Completed exchanges <span class="badge">${completed.length}</span></h2></div>
      ${completed.length ? completed.map(({ loan, side, reviewSubmitted }) => loanCard(loan, side, reviewSubmitted)).join("") : emptyState("No completed exchanges yet", "Returned books will appear here, with a review form for each participant.")}</section>`;
    main.insertAdjacentHTML("beforeend", completedSection);
  }
  async function renderWishlist() {
    if (!requireSession()) return;
    loading("Opening your wishlist…");
    const wishlist = await api("/api/wishlist");
    appState.wishlistIds = new Set((wishlist.books || []).map((book) => book.id));
    setMain(`${pageHeading("SAVED FOR LATER", "Your wishlist.", "Keep an eye on books you hope to read. We'll notify you when a saved book becomes available.")}${wishlist.books.length ? `<div class="grid book-grid">${wishlist.books.map(bookCard).join("")}</div>` : emptyState("Your wishlist is empty", "Save books from the catalog and find them here.", `<a class="button button-small" href="#/catalog">Browse books</a>`)}`);
  }
  async function renderProfile(userId) {
    if (!userId) {
      if (!requireSession()) return;
      userId = session().user.id;
    }
    loading("Loading reader profile…");
    const [profile, reviews, wishlist] = await Promise.all([
      api(`/api/users/${encodeURIComponent(userId)}/profile`),
      api(`/api/users/${encodeURIComponent(userId)}/reviews`),
      session() ? api("/api/wishlist") : Promise.resolve({ books: [] })
    ]);
    appState.wishlistIds = new Set((wishlist.books || []).map((book) => book.id));
    appState.currentProfile = profile;
    const currentProfile = session()?.user?.id === profile.user.id;
    const initials = (profile.user.displayName || "R").trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
    setMain(`${pageHeading("COMMUNITY READER", currentProfile ? "Your reader profile." : `${profile.user.displayName}'s profile.`, "A public look at books shared and community exchanges.")}
      <section class="card panel">
        <div class="profile-head"><div class="avatar" aria-hidden="true">${escapeHtml(initials)}</div><div><h2>${escapeHtml(profile.user.displayName)}</h2><p class="page-intro">${escapeHtml(profile.user.cityOrPostalCode || "Community reader")}</p></div></div>
        <div class="trust-score"><strong>${Number(profile.trustScore.score).toFixed(2)}</strong><span>Trust score<br>from ${profile.trustScore.reviewCount} review${profile.trustScore.reviewCount === 1 ? "" : "s"}</span></div>
        ${!currentProfile && session() ? `<div class="button-row" style="margin-top:15px"><button class="button button-outline" data-action="message-user" data-id="${escapeHtml(profile.user.id)}" type="button">Message reader</button></div>` : ""}
      </section>
      <div class="grid split-grid" style="margin-top:18px">
        <section class="card panel"><div class="section-title"><h2>Listed books (${profile.listedBooks.length})</h2></div>
          ${profile.listedBooks.length ? `<div class="grid book-grid">${profile.listedBooks.map(bookCard).join("")}</div>` : emptyState("No books listed yet", "Books shared by this reader will appear here.")}</section>
        <section class="card panel"><div class="section-title"><h2>Borrowing history</h2></div>
          ${profile.borrowingHistory.length ? profile.borrowingHistory.map((loan) => `<article class="list-card"><h3>${escapeHtml(loan.bookTitle)}</h3><p>From ${escapeHtml(loan.ownerName)} · ${dateText(loan.startDate)} · ${escapeHtml(loan.status.toLowerCase())}</p></article>`).join("") : emptyState("No borrowing history", "Completed exchanges will appear here.")}</section>
        <section class="card panel"><div class="section-title"><h2>Lending history</h2></div>
          ${profile.lendingHistory.length ? profile.lendingHistory.map((loan) => `<article class="list-card"><h3>${escapeHtml(loan.bookTitle)}</h3><p>To ${escapeHtml(loan.borrowerName)} · ${dateText(loan.startDate)} · ${escapeHtml(loan.status.toLowerCase())}</p></article>`).join("") : emptyState("No lending history", "Books lent to readers will appear here.")}</section>
        <section class="card panel"><div class="section-title"><h2>Received reviews (${reviews.length})</h2></div>
          ${reviews.length ? reviews.map((review) => `<article class="list-card"><div class="list-card-header"><h3>${escapeHtml(review.reviewerName)}</h3><span class="badge">${Number(review.averageRating).toFixed(1)} / 5</span></div><p>Reliability ${review.reliability}/5 · Communication ${review.communication}/5 · Book care ${review.careOfBooks}/5</p>${review.comment ? `<p>${escapeHtml(review.comment)}</p>` : ""}</article>`).join("") : emptyState("No reviews yet", "Reviews from completed exchanges will appear here.")}</section>
      </div>`);
  }
  async function renderForum() {
    if (!requireSession()) return;
    loading("Loading community posts…");
    const posts = await api("/api/forum/posts");
    const ownId = session()?.user?.id;
    setMain(`${pageHeading("THE READING ROOM", "Community forum.", "Share recommendations, build reading lists, and tell neighbors about reading events.", session() ? `<button class="button" type="button" data-action="new-post">＋ Write a post</button>` : `<a class="button button-outline" href="#/login">Sign in to post</a>`)}
      <div id="post-form-slot"></div>
      <div class="grid">${posts.length ? posts.map((post) => `<article class="card post-card"><div class="list-card-header"><span class="badge">${escapeHtml(post.category.replaceAll("_", " ").toLowerCase())}</span><small class="small-meta">${dateText(post.createdAt)}</small></div><h3>${escapeHtml(post.title)}</h3><p class="small-meta">By <a href="#/profile/${encodeURIComponent(post.authorId)}">${escapeHtml(post.authorName)}</a>${post.eventDate ? ` · Event ${dateText(post.eventDate)}` : ""}</p><div class="post-content">${escapeHtml(post.content)}</div>
        ${ownId === post.authorId ? `<details style="margin-top:12px"><summary class="button button-small button-outline" style="display:inline-flex">Edit post</summary>${postForm(post)}</details><div class="button-row" style="margin-top:9px"><button class="button button-small button-danger" data-action="delete-post" data-id="${escapeHtml(post.id)}" type="button">Delete</button></div>` : ""}
      </article>`).join("") : emptyState("No posts yet", "Start a conversation with your community.")}</div>`);
  }
  function postForm(post = null) {
    return `<form class="card form-card" style="margin:12px 0" data-form="forum-post" data-id="${escapeHtml(post?.id || "")}">
      <div class="form-grid"><div class="field"><label for="post-category-${escapeHtml(post?.id || "new")}">Post type</label><select id="post-category-${escapeHtml(post?.id || "new")}" name="category"><option value="RECOMMENDATION"${post?.category === "RECOMMENDATION" ? " selected" : ""}>Recommendation</option><option value="READING_LIST"${post?.category === "READING_LIST" ? " selected" : ""}>Reading list</option><option value="READING_EVENT"${post?.category === "READING_EVENT" ? " selected" : ""}>Reading event</option></select></div>
      <div class="field"><label for="post-title-${escapeHtml(post?.id || "new")}">Title</label><input id="post-title-${escapeHtml(post?.id || "new")}" name="title" value="${escapeHtml(post?.title || "")}" maxlength="160" required></div>
      <div class="field"><label for="post-date-${escapeHtml(post?.id || "new")}">Event date (optional)</label><input id="post-date-${escapeHtml(post?.id || "new")}" name="eventDate" type="datetime-local" value="${escapeHtml(localDateTimeValue(post?.eventDate))}"></div>
      <div class="field field-wide"><label for="post-content-${escapeHtml(post?.id || "new")}">Your post</label><textarea id="post-content-${escapeHtml(post?.id || "new")}" name="content" maxlength="10000" required>${escapeHtml(post?.content || "")}</textarea></div></div>
      <div class="button-row" style="margin-top:13px"><button class="button button-small" type="submit">${post ? "Save changes" : "Publish post"}</button><button class="button button-small button-outline" type="button" data-action="close-post-form">Cancel</button></div>
    </form>`;
  }
  async function renderMessages(otherUserId = "") {
    if (!requireSession()) return;
    let conversations = [];
    if (otherUserId) conversations = await api(`/api/messages/with/${encodeURIComponent(otherUserId)}`);
    const user = session().user;
    setMain(`${pageHeading("PRIVATE CONVERSATIONS", "Messages.", "Send a private note to another BookBridge reader. Only you and the recipient can see your conversation.")}
      <section class="card panel">
        <form class="inline-form" data-form="open-conversation"><div class="field"><label for="message-user-id">Reader's profile ID</label><input id="message-user-id" name="otherUserId" value="${escapeHtml(otherUserId)}" placeholder="Copy the ID from their profile" required></div><button class="button button-small" type="submit">Open conversation</button></form>
        ${otherUserId ? `<h2 style="margin-top:20px">Conversation</h2><p class="field-hint">Messages are private between the two participants.</p>
          <div class="message-thread" aria-live="polite">${conversations.length ? conversations.map((message) => `<article class="message-bubble${message.senderId === user.id ? " mine" : ""}"><p>${escapeHtml(message.content)}</p><small>${escapeHtml(message.senderName)} · ${dateText(message.createdAt)}${message.readAt && message.senderId === user.id ? " · Read" : ""}</small></article>`).join("") : `<p class="small-meta">No messages yet. Send the first note below.</p>`}</div>
          <form class="inline-form message-form" data-form="send-message"><input type="hidden" name="recipientId" value="${escapeHtml(otherUserId)}"><div class="field"><label for="message-content">Your message</label><textarea id="message-content" name="content" maxlength="4000" required placeholder="Write a kind, helpful message…"></textarea></div><button class="button" type="submit">Send message</button></form>` : emptyState("Choose a reader", "Enter the profile ID of the person you want to message.") }
      </section>`);
  }
  async function renderNotifications() {
    if (!requireSession()) return;
    loading("Checking your notifications…");
    const [notifications, unread] = await Promise.all([api("/api/notifications"), api("/api/notifications/unread")]);
    setMain(`${pageHeading("UPDATES", "Your notifications.", `${unread.length} unread update${unread.length === 1 ? "" : "s"} from your books and community.`, unread.length ? `<button class="button button-outline" data-action="mark-all-read" type="button">Mark all read</button>` : "")}
      <section class="card panel">${notifications.length ? notifications.map((item) => `<article class="notification-item${item.read ? "" : " unread"}"><div><p>${escapeHtml(item.message)}</p><small>${escapeHtml(item.type.replaceAll("_", " ").toLowerCase())} · ${dateText(item.createdAt)}</small></div>${item.read ? `<span class="badge">Read</span>` : `<button class="button button-small button-outline" data-action="mark-read" data-id="${escapeHtml(item.id)}" type="button">Mark read</button>`}</article>`).join("") : emptyState("No notifications yet", "We'll let you know when something needs your attention.")}</section>`);
  }
  function renderMore() {
    const links = [
      ["wishlist", "♡", "Wishlist", "Books saved for later"],
      ["forum", "✳", "Community forum", "Recommendations, lists, and events"],
      ["messages", "✉", "Private messages", "Conversations with readers"],
      ["notifications", "♧", "Notifications", "Requests and community updates"],
      ["profile", "◎", "My profile", "Your books, history, and trust score"]
    ];
    setMain(`${pageHeading("MORE FROM BOOKBRIDGE", "Your community shortcuts.", "Find the other pages in your reader account.")}<div class="grid split-grid">${links.map(([route, icon, title, description]) => `<a class="card panel shortcut-card" href="#/${route}"><span class="shortcut-icon" aria-hidden="true">${icon}</span><h2>${title}</h2><p class="page-intro">${description}</p><span class="button button-small button-outline">Open page →</span></a>`).join("")}</div>`);
  }
  function toDateTime(value) {
    if (!value) return null;
    return new Date(value).toISOString();
  }

  document.addEventListener("submit", async (event) => {
    const form = event.target.closest("form[data-form]");
    if (!form) return;
    event.preventDefault();
    if (form.dataset.form !== "login" && form.dataset.form !== "register" && form.dataset.form !== "catalog-filters") {
      if (!requireSession()) return;
    }
    const submitter = event.submitter;
    const values = Object.fromEntries(new FormData(form).entries());
    const button = submitter || form.querySelector('button[type="submit"]');
    const priorText = button?.textContent;
    if (button) { button.disabled = true; button.textContent = "Please wait…"; }
    try {
      switch (form.dataset.form) {
        case "login":
        case "register": {
          const registering = form.dataset.form === "register";
          const result = await api(`/api/auth/${registering ? "register" : "login"}`, {
            method: "POST", body: JSON.stringify(values)
          });
          sessionStorage.setItem(SESSION_KEY, JSON.stringify(result));
          updateNavigation();
          showToast(registering ? "Welcome to BookBridge. Your account is ready." : "Welcome back.");
          location.hash = "#/dashboard";
          break;
        }
        case "catalog-filters": {
          const query = new URLSearchParams(values);
          location.hash = `#/catalog?${query.toString()}`;
          break;
        }
        case "book": {
          const editing = Boolean(form.dataset.id);
          const payload = {
            ...values,
            loanDurationDays: values.loanDurationDays ? Number(values.loanDurationDays) : 14,
            status: values.status || "AVAILABLE"
          };
          await api(editing ? `/api/books/${encodeURIComponent(form.dataset.id)}` : "/api/books", {
            method: editing ? "PUT" : "POST", body: JSON.stringify(payload)
          });
          showToast(editing ? "Your book listing has been updated." : "Your book is now on the community shelf.");
          location.hash = editing ? `#/book/${encodeURIComponent(form.dataset.id)}` : "#/bookshelf";
          break;
        }
        case "borrow": {
          await api(`/api/borrow-requests/books/${encodeURIComponent(form.dataset.id)}`, {
            method: "POST",
            body: JSON.stringify({
              desiredStartDate: values.desiredStartDate,
              loanDurationDays: Number(values.loanDurationDays),
              personalMessage: values.personalMessage
            })
          });
          showToast("Your borrow request was sent to the owner.");
          location.hash = "#/bookshelf";
          break;
        }
        case "borrow-decision": {
          const decision = submitter?.value;
          if (!decision) throw new Error("Choose whether to approve or decline this request.");
          const payload = {
            decision,
            responseMessage: values.responseMessage || ""
          };
          if (decision === "APPROVE") {
            if (!values.handoverDate || !values.handoverLocation?.trim()) {
              throw new Error("Add a handover date and location before approving.");
            }
            payload.exchangeMethod = values.exchangeMethod;
            payload.handoverDate = values.handoverDate;
            payload.handoverLocation = values.handoverLocation.trim();
          }
          await api(`/api/borrow-requests/${encodeURIComponent(form.dataset.id)}/decision`, {
            method: "PATCH", body: JSON.stringify(payload)
          });
          showToast(decision === "APPROVE" ? "Borrow request approved; the loan has been created." : "Borrow request declined.");
          await renderBookshelf();
          break;
        }
        case "extension": {
          await api(`/api/loans/${encodeURIComponent(form.dataset.id)}/extensions`, {
            method: "POST",
            body: JSON.stringify({ additionalDays: Number(values.additionalDays), message: values.message || "" })
          });
          showToast("Your extension request was sent to the owner.");
          await renderLoans();
          break;
        }
        case "extension-decision": {
          await api(`/api/loans/extensions/${encodeURIComponent(form.dataset.id)}/decision`, {
            method: "PATCH",
            body: JSON.stringify({ decision: submitter?.value, responseMessage: values.responseMessage || "" })
          });
          showToast(submitter?.value === "APPROVE" ? "Extension approved." : "Extension declined.");
          await renderLoans();
          break;
        }
        case "review": {
          await api(`/api/loans/${encodeURIComponent(form.dataset.id)}/reviews`, {
            method: "POST",
            body: JSON.stringify({
              reliability: Number(values.reliability),
              communication: Number(values.communication),
              careOfBooks: Number(values.careOfBooks),
              comment: values.comment || ""
            })
          });
          const reviewedUserId = form.dataset.reviewedUserId;
          const reviewPanel = document.createElement("div");
          reviewPanel.className = "success-panel review-submitted";
          reviewPanel.innerHTML = `<strong>Review submitted.</strong> Your review is saved. Refreshing the reader's public profile…`;
          form.replaceWith(reviewPanel);
          try {
            const [profile, reviews] = await Promise.all([
              api(`/api/users/${encodeURIComponent(reviewedUserId)}/profile`),
              api(`/api/users/${encodeURIComponent(reviewedUserId)}/reviews`)
            ]);
            const updatedReview = reviews.find((review) =>
              review.loanId === form.dataset.id && review.reviewerId === session()?.user?.id);
            reviewPanel.innerHTML = `<strong>Review submitted.</strong> ${updatedReview ? `Trust score updated to ${Number(profile.trustScore.score).toFixed(2)} from ${profile.trustScore.reviewCount} review${profile.trustScore.reviewCount === 1 ? "" : "s"}.` : "The public profile has been refreshed."} <a href="#/profile/${encodeURIComponent(reviewedUserId)}">View updated profile</a>`;
            showToast("Review submitted. The reader's public reviews and trust score were refreshed.");
          } catch (error) {
            reviewPanel.innerHTML = `<strong>Review submitted.</strong> The review is saved, but the public profile could not be refreshed: ${escapeHtml(error.message)} <a href="#/profile/${encodeURIComponent(reviewedUserId)}">Try viewing the profile</a>`;
            showToast("Review submitted, but the public profile refresh failed.", true);
          }
          break;
        }
        case "forum-post": {
          const editing = Boolean(form.dataset.id);
          const payload = {
            category: values.category,
            title: values.title,
            content: values.content,
            eventDate: values.eventDate ? toDateTime(values.eventDate) : null
          };
          await api(editing ? `/api/forum/posts/${encodeURIComponent(form.dataset.id)}` : "/api/forum/posts", {
            method: editing ? "PUT" : "POST", body: JSON.stringify(payload)
          });
          showToast(editing ? "Your post has been updated." : "Your post has been published.");
          await renderForum();
          break;
        }
        case "open-conversation":
          if (values.otherUserId === session().user.id) throw new Error("Choose another reader's profile ID.");
          location.hash = `#/messages?with=${encodeURIComponent(values.otherUserId.trim())}`;
          break;
        case "send-message":
          await api("/api/messages", { method: "POST", body: JSON.stringify(values) });
          showToast("Your private message was sent.");
          await renderMessages(values.recipientId);
          break;
      }
    } catch (error) {
      showToast(error.message || "That action could not be completed.", true);
    } finally {
      if (button?.isConnected) { button.disabled = false; button.textContent = priorText || button.textContent; }
    }
  });

  document.addEventListener("click", async (event) => {
    const control = event.target.closest("[data-action]");
    if (!control) return;
    const { action, id } = control.dataset;
    try {
      if (action === "retry") { location.hash = control.dataset.hash || "#/catalog"; await renderRoute(); }
      if (action === "toggle-wishlist") {
        if (!requireSession()) return;
        const adding = !appState.wishlistIds.has(id);
        const result = await api(`/api/wishlist/${encodeURIComponent(id)}`, { method: adding ? "PUT" : "DELETE" });
        appState.wishlistIds = new Set((result.books || []).map((book) => book.id));
        showToast(adding ? "Book saved to your wishlist." : "Book removed from your wishlist.");
        await renderRoute();
      }
      if (action === "isbn-lookup") {
        const isbn = document.querySelector("#book-isbn")?.value.trim();
        if (!isbn) { showToast("Enter an ISBN first.", true); return; }
        control.disabled = true;
        const metadata = await api(`/api/books/isbn/${encodeURIComponent(isbn)}`);
        ["title", "author", "genre", "language", "description", "publisher", "coverImageUrl"].forEach((key) => {
          const input = document.querySelector(`[name="${key}"]`);
          if (input && metadata[key]) input.value = metadata[key];
        });
        showToast(metadata.title ? "Book details found. Check them before saving." : "No book record found. You can enter details manually.", !metadata.title);
      }
      if (action === "borrow-form") {
        if (!requireSession()) return;
        const slot = document.querySelector("#borrow-form-slot");
        if (slot) slot.innerHTML = `<form class="card form-card" style="margin-top:18px" data-form="borrow" data-id="${escapeHtml(id)}"><h2>Request this book</h2><div class="form-grid"><div class="field"><label for="borrow-date">Desired start date</label><input id="borrow-date" name="desiredStartDate" type="date" min="${new Date().toISOString().slice(0, 10)}" value="${new Date(Date.now() + 86400000).toISOString().slice(0, 10)}" required></div><div class="field"><label for="borrow-duration">Loan duration (days)</label><input id="borrow-duration" name="loanDurationDays" type="number" min="1" max="365" value="14" required></div><div class="field field-wide"><label for="borrow-message">Personal message (optional)</label><textarea id="borrow-message" name="personalMessage" maxlength="1000"></textarea></div></div><button class="button" type="submit">Send request</button></form>`;
        slot.scrollIntoView({ behavior: "smooth", block: "nearest" });
      }
      if (action === "message-user") {
        if (!requireSession()) return;
        if (!id) throw new Error("This reader's profile is not available.");
        location.hash = `#/messages?with=${encodeURIComponent(id)}`;
      }
      if (action === "change-book-status") {
        if (!requireSession()) return;
        await api(`/api/books/${encodeURIComponent(id)}/status`, {
          method: "PATCH", body: JSON.stringify({ status: control.dataset.status })
        });
        showToast("Book availability updated.");
        await renderBook(id);
      }
      if (action === "delete-book") {
        if (!requireSession()) return;
        if (!confirm("Delete this book listing? This cannot be undone.")) return;
        await api(`/api/books/${encodeURIComponent(id)}`, { method: "DELETE" });
        showToast("Book listing deleted.");
        location.hash = "#/bookshelf";
      }
      if (action === "show-extension") {
        const slot = document.querySelector(`#extension-slot-${CSS.escape(id)}`);
        if (slot) slot.innerHTML = `<form class="card form-card" style="margin-top:12px" data-form="extension" data-id="${escapeHtml(id)}"><div class="field"><label for="extension-days-${escapeHtml(id)}">Extra days requested</label><input id="extension-days-${escapeHtml(id)}" name="additionalDays" type="number" min="1" max="365" value="7" required></div><div class="field"><label for="extension-message-${escapeHtml(id)}">Message (optional)</label><input id="extension-message-${escapeHtml(id)}" name="message" maxlength="1000"></div><button class="button button-small" type="submit">Send extension request</button></form>`;
      }
      if (action === "return-loan") {
        if (!requireSession()) return;
        if (!confirm("Mark this book as returned? The book will become available again.")) return;
        await api(`/api/loans/${encodeURIComponent(id)}/return`, { method: "POST" });
        showToast("Book marked returned. You can now review the exchange.");
        await renderLoans();
      }
      if (action === "mark-read") {
        await api(`/api/notifications/${encodeURIComponent(id)}/read`, { method: "PATCH" });
        showToast("Notification marked as read.");
        await renderNotifications();
        refreshNotificationCount();
      }
      if (action === "mark-all-read") {
        await api("/api/notifications/read-all", { method: "PATCH" });
        showToast("All notifications marked as read.");
        await renderNotifications();
        refreshNotificationCount();
      }
      if (action === "new-post") {
        if (!requireSession()) return;
        document.querySelector("#post-form-slot").innerHTML = postForm();
        document.querySelector("#post-form-slot").scrollIntoView({ behavior: "smooth", block: "start" });
      }
      if (action === "close-post-form") {
        const form = control.closest("form");
        if (form?.dataset.id) form.closest("details")?.removeAttribute("open");
        else form?.remove();
      }
      if (action === "delete-post") {
        if (!requireSession()) return;
        if (!confirm("Delete this community post?")) return;
        await api(`/api/forum/posts/${encodeURIComponent(id)}`, { method: "DELETE" });
        showToast("Post deleted.");
        await renderForum();
      }
    } catch (error) {
      showToast(error.message || "That action could not be completed.", true);
    } finally {
      if (control.isConnected) control.disabled = false;
    }
  });

  document.addEventListener("change", async (event) => {
    if (event.target.id === "csv-upload" && event.target.files?.[0]) {
      await uploadCsv(event.target.files[0], event.target);
    }
  });
  document.addEventListener("error", (event) => {
    const image = event.target.closest?.("img[data-cover-title]");
    if (!image) return;
    const placeholder = document.createElement("span");
    placeholder.className = "cover-placeholder";
    placeholder.setAttribute("aria-hidden", "true");
    placeholder.textContent = image.dataset.coverTitle || "A good read";
    image.replaceWith(placeholder);
  }, true);
  async function uploadCsv(file, input) {
    if (!requireSession()) return;
    const form = new FormData();
    form.append("file", file);
    input.disabled = true;
    try {
      const books = await api("/api/books/bulk", { method: "POST", body: form });
      showToast(`${books.length} book${books.length === 1 ? "" : "s"} added from your CSV.`);
      await renderBookshelf();
    } catch (error) {
      showToast(error.message || "CSV upload failed. Check the required columns and file.", true);
    } finally {
      input.disabled = false;
      input.value = "";
    }
  }
  document.querySelector("#logout-button").addEventListener("click", () => {
    sessionStorage.removeItem(SESSION_KEY);
    appState.wishlistIds.clear();
    updateNavigation();
    showToast("You have signed out.");
    location.hash = "#/catalog";
  });
  window.addEventListener("hashchange", renderRoute);
  window.addEventListener("storage", (event) => {
    if (event.key === SESSION_KEY) updateNavigation();
  });

  if (!location.hash) location.hash = "#/catalog";
  updateNavigation();
  renderRoute();
})();
