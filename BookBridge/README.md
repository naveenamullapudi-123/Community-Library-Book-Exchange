# BookBridge - Community Library Book Exchange

BookBridge helps neighbors share books. This project uses Java 17+, Spring Boot, Maven, MongoDB, REST APIs, and a responsive HTML/CSS/vanilla JavaScript frontend.

## Current progress

Stage 5 provides the complete browser interface for the implemented API: registration/login, community catalog and filters, book details/listings/CSV upload, bookshelf and borrow requests, loans/extensions/returns/reviews, wishlist, public profiles, forum, private messages, and notifications. It is served by Spring Boot from the same origin as the REST API.

## Prerequisites

- Java 17 or newer (a JDK, not only a JRE).
- MongoDB Community Server running locally, or a MongoDB connection URI.
- An internet connection on the first Maven Wrapper run so it can download Maven and dependencies.
- MongoDB Compass is optional for viewing the `bookbridge` database.

## Start MongoDB and BookBridge in VS Code

Install MongoDB Community Server and ensure its default port `27017` is available. If MongoDB was installed as a Windows service, open a PowerShell terminal (as Administrator if required) and start it:

```powershell
Get-Service MongoDB
Start-Service MongoDB
```

If you installed MongoDB without a Windows service, start the server in its own terminal instead:

```powershell
New-Item -ItemType Directory -Force C:\data\db
mongod --dbpath C:\data\db
```

In another VS Code PowerShell terminal, open the backend folder:

```powershell
cd backend
```

If the Maven Wrapper reports that `JAVA_HOME` is missing, set it from your installed JDK (run this in the same terminal):

```powershell
$javaHomeLine = (java -XshowSettings:properties -version 2>&1 | Select-String "java.home =").ToString()
$env:JAVA_HOME = $javaHomeLine -replace "^\s*java.home\s*=\s*", ""
```

By default, the backend connects to `mongodb://localhost:27017/bookbridge` and listens on port `8080`. Start it with:

```powershell
.\mvnw.cmd spring-boot:run
```

For a different MongoDB URI, set the environment variable before starting the backend:

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/bookbridge"
.\mvnw.cmd spring-boot:run
```

The API signs 24-hour bearer tokens with a randomly generated key when `BOOKBRIDGE_TOKEN_SECRET` is not set. Those tokens become invalid when the application restarts. For predictable restarts, configure a private secret of at least 32 bytes in the environment. Do not commit the secret to the project.

```powershell
$bytes = New-Object byte[] 48
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($bytes)
$env:BOOKBRIDGE_TOKEN_SECRET = [Convert]::ToBase64String($bytes)
$random.Dispose()
.\mvnw.cmd spring-boot:run
```

Open the application at `http://localhost:8080/` and check the health endpoint at `http://localhost:8080/api/health`. The frontend and REST API are served by the same Spring Boot process; no separate frontend server or build step is needed. MongoDB collections and indexes are created when the application accesses them. In Compass, connect to `mongodb://localhost:27017` and open the `bookbridge` database.

## Run tests

From the `backend` folder:

```powershell
.\mvnw.cmd test
```

The standard tests cover the health endpoint, authentication and password hashing, bearer-token checks, book ownership, and CSV validation. Some API integration tests require the MongoDB flag below. To verify real MongoDB persistence and borrowing/community workflows, start MongoDB and run:

```powershell
.\mvnw.cmd -Dbookbridge.mongo-tests=true test
```

The optional MongoDB integration tests exercise real user registration, book CRUD, borrow request decisions, loan creation, extensions, returns, wishlists, reviews, profiles, forum posts, private messages, notifications, static frontend delivery, and persistence. They remove the test records they create.

## Stage 2 API quick test

The examples below use PowerShell in a second terminal while the backend is running.

### Register and keep the returned token

```powershell
$account = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" `
  -Body (@{ displayName = "Sample Reader"; email = "reader@example.test"; password = "choose-a-private-password"; cityOrPostalCode = "Springfield" } | ConvertTo-Json)
$token = $account.token
```

Registration stores a BCrypt password hash, not the submitted password. Use a new email for each registration; duplicate email addresses return `409 Conflict`.

### Add a book and browse it

```powershell
$headers = @{ Authorization = "Bearer $token" }
$book = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/books `
  -Headers $headers -ContentType "application/json" `
  -Body (@{
    isbn = "9780140328721"
    title = "Fantastic Mr Fox"
    author = "Roald Dahl"
    genre = "Children"
    language = "English"
    condition = "GOOD"
  } | ConvertTo-Json)

Invoke-RestMethod "http://localhost:8080/api/books?q=Fantastic"
Invoke-RestMethod "http://localhost:8080/api/books/genres"
Invoke-RestMethod "http://localhost:8080/api/books/$($book.id)"
Invoke-RestMethod "http://localhost:8080/api/books/me/bookshelf" -Headers $headers
```

Book creation, editing, status changes, deletion, CSV upload, and the personal bookshelf require `Authorization: Bearer <token>`. Public browsing, book details, genre browsing, and ISBN lookup do not require a token. The owner ID is taken from the verified token, never from the book request body.

For search, use `q` for title, author, or genre, `status` for `AVAILABLE`, `LENT_OUT`, or `UNAVAILABLE`, `language` for a case-insensitive partial match, and `condition` for `EXCELLENT`, `GOOD`, `ACCEPTABLE`, or `WORN_BUT_READABLE`. Without a `status` filter, browsing returns available books. Results are limited to 200 books.

### Edit, change availability, or delete

```powershell
$book.status = "UNAVAILABLE"
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/books/$($book.id)" `
  -Headers $headers -ContentType "application/json" -Body ($book | ConvertTo-Json)

Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/books/$($book.id)/status" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{ status = "AVAILABLE" } | ConvertTo-Json)

Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/books/$($book.id)" -Headers $headers
```

Only the book owner can edit its details, change its availability, or delete it. An unauthorized owner receives `403 Forbidden`.

### ISBN lookup and manual entry

```powershell
Invoke-RestMethod "http://localhost:8080/api/books/isbn/9780140328721"
```

The lookup uses Open Library's public API. If it has no record or is unavailable, enter the book details manually using `POST /api/books`.

### CSV bulk upload

Save a CSV file with required `isbn`, `title`, `author`, and `condition` columns. Optional columns are `genre`, `language`, `description`, `publisher`, `coverImageUrl`, `status`, `loanDurationDays`, `pickupPreferences`, and `cityOrPostalCode`. Example:

```csv
isbn,title,author,condition,genre,language
9780140328721,Fantastic Mr Fox,Roald Dahl,Good,Children,English
```

Upload up to 500 books at once. The entire file is validated before any rows are saved.

```powershell
curl.exe -X POST -H "Authorization: Bearer $token" -F "file=@books.csv" http://localhost:8080/api/books/bulk
```

## Stage 3 API quick test

Register two accounts with different email addresses. Change the example addresses if you have already used them:

```powershell
$ownerAccount = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" `
  -Body (@{ displayName = "Book Owner"; email = "owner@example.test"; password = "choose-a-private-password" } | ConvertTo-Json)
$ownerToken = $ownerAccount.token

$borrowerAccount = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" `
  -Body (@{ displayName = "Book Borrower"; email = "borrower@example.test"; password = "choose-another-private-password" } | ConvertTo-Json)
$borrowerToken = $borrowerAccount.token

$book = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/books `
  -Headers @{ Authorization = "Bearer $ownerToken" } `
  -ContentType "application/json" `
  -Body (@{ isbn = "9780140328721"; title = "A Shared Book"; author = "A Reader"; condition = "GOOD" } | ConvertTo-Json)
```

The borrower requests the available book:

```powershell
$startDate = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
$borrowRequest = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/borrow-requests/books/$($book.id)" `
  -Headers @{ Authorization = "Bearer $borrowerToken" } `
  -ContentType "application/json" `
  -Body (@{
    desiredStartDate = $startDate
    loanDurationDays = 14
    personalMessage = "I would love to borrow this book."
  } | ConvertTo-Json)
```

Only the book owner can view incoming requests or decide them. Approval requires an exchange method, a handover date on or after the requested start date, and an agreed location. It creates the loan and marks the book `LENT_OUT`; the due date is the requested start date plus the approved loan duration. Rejection can include a response message.

```powershell
Invoke-RestMethod "http://localhost:8080/api/borrow-requests/incoming" `
  -Headers @{ Authorization = "Bearer $ownerToken" }

$decision = @{
  decision = "APPROVE"
  exchangeMethod = "IN_PERSON"
  handoverDate = $startDate
  handoverLocation = "Community library"
  responseMessage = "That works for me."
}
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/borrow-requests/$($borrowRequest.id)/decision" `
  -Headers @{ Authorization = "Bearer $ownerToken" } `
  -ContentType "application/json" -Body ($decision | ConvertTo-Json)
```

```powershell
$loan = Invoke-RestMethod "http://localhost:8080/api/loans/borrowed" `
  -Headers @{ Authorization = "Bearer $borrowerToken" } |
  Where-Object { $_.bookId -eq $book.id } | Select-Object -First 1
```

Other useful endpoints:

| Method and path | Purpose |
|---|---|
| `GET /api/borrow-requests/mine` | View your submitted requests and their statuses. |
| `GET /api/books/me/bookshelf` | View your listed books and incoming pending requests. |
| `GET /api/loans/borrowed` | View your active borrowed books and due dates. |
| `GET /api/loans/lent` | View your active lent-out books and due dates. |
| `POST /api/loans/{loanId}/extensions` | Borrower asks to extend an active loan with `additionalDays` and an optional message. |
| `GET /api/loans/extensions/incoming` | Owner views pending extension requests. |
| `PATCH /api/loans/extensions/{extensionId}/decision` | Owner approves or rejects an extension; approval updates the due date. |
| `POST /api/loans/{loanId}/return` | Borrower or owner marks an active loan returned and makes its book available. A repeat return is rejected. |
| `GET /api/notifications` | View your latest in-app notifications (up to 100). |

For example, the owner can approve an extension with:

```powershell
$extension = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/loans/$($loan.id)/extensions" `
  -Headers @{ Authorization = "Bearer $borrowerToken" } `
  -ContentType "application/json" `
  -Body (@{ additionalDays = 7; message = "Could I have one more week?" } | ConvertTo-Json)

Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/loans/extensions/$($extension.id)/decision" `
  -Headers @{ Authorization = "Bearer $ownerToken" } `
  -ContentType "application/json" `
  -Body (@{ decision = "APPROVE"; responseMessage = "Approved." } | ConvertTo-Json)
```

An application scheduler creates in-app reminders for both loan participants at 8:05 AM server-local time, three days before the due date and on the due date. Reminder IDs are deterministic, so repeated scheduler runs do not create duplicate reminders. Override the schedule with `BOOKBRIDGE_REMINDERS_CRON` if needed. Email reminders are not configured or sent; SMTP delivery remains future work.

## Stage 4 API quick test

The following examples use the `$ownerToken`, `$borrowerToken`, `$book`, and `$loan` values from the Stage 3 API example. Add the token to protected requests with `-Headers @{ Authorization = "Bearer $borrowerToken" }` or the appropriate user's token.

### Wishlist and availability notifications

```powershell
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/wishlist/$($book.id)" `
  -Headers @{ Authorization = "Bearer $borrowerToken" }
Invoke-RestMethod "http://localhost:8080/api/wishlist" `
  -Headers @{ Authorization = "Bearer $borrowerToken" }
Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/wishlist/$($book.id)" `
  -Headers @{ Authorization = "Bearer $borrowerToken" }
```

When a saved ISBN becomes available, wishlist owners receive an in-app notification. Each availability transition is deduplicated; a later transition from unavailable to available can notify again. A repeated request that leaves a book available does not create another notification.

### Reviews and profiles

Only the borrower or owner can review the other participant after the loan is returned. Each participant can submit one review per loan. The three rating fields accept values from 1 to 5:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/loans/$($loan.id)/reviews" `
  -Headers @{ Authorization = "Bearer $borrowerToken" } `
  -ContentType "application/json" `
  -Body (@{ reliability = 5; communication = 4; careOfBooks = 5; comment = "A great exchange." } | ConvertTo-Json)
```

Public profiles and received reviews are available without authentication. Profile responses contain public display/location data, listed books, borrowing/lending history, and trust score; they do not include email addresses, password hashes, or tokens.

| Method and path | Purpose |
|---|---|
| `GET /api/users/{userId}/profile` | Public profile, books, loan history, and trust score. |
| `GET /api/users/{userId}/reviews` | Public reviews received by a user. |
| `GET /api/loans/returned` | Authenticated borrower's and owner's completed exchanges. |
| `GET /api/loans/{loanId}/reviews` | List reviews for a returned loan (authenticated participants only); used by the UI to show whether the current user has submitted theirs. |
| `POST /api/loans/{loanId}/reviews` | Submit a review after return (authenticated participant only). |

The trust score is the arithmetic mean of reliability, communication, and care-of-books ratings across received reviews, rounded to two decimal places. A user with no reviews has score `0.0` and review count `0`.

### Forum and private messages

Forum endpoints require a bearer token. A post has a free-text `category` (up to 40 characters), `title`, `content`, and optional `eventDate`; categories can be `RECOMMENDATION`, `READING_LIST`, or `READING_EVENT`. Authors alone can edit or delete their posts.

| Method and path | Purpose |
|---|---|
| `GET /api/forum/posts` | Browse community posts. |
| `GET /api/forum/posts/{postId}` | View a post. |
| `POST /api/forum/posts` | Create a post with `category`, `title`, `content`, and optional `eventDate`. |
| `PUT /api/forum/posts/{postId}` | Edit your own post. |
| `DELETE /api/forum/posts/{postId}` | Delete your own post. |
| `POST /api/messages` | Send a message using `recipientId` and `content`. |
| `GET /api/messages/with/{otherUserId}` | View the conversation with that user. |

Only the two participants can read a conversation. Opening a conversation marks messages addressed to the current user as read.

### Notification center

| Method and path | Purpose |
|---|---|
| `GET /api/notifications` | List your latest 100 notifications. |
| `GET /api/notifications/unread` | List your latest 100 unread notifications. |
| `PATCH /api/notifications/{notificationId}/read` | Mark one of your notifications as read. |
| `PATCH /api/notifications/read-all` | Mark all your unread notifications as read. |

Notifications are private to the authenticated user and cover wishlist availability, borrow-request decisions, new messages, extension requests/decisions, returns, reviews, and due-date reminders. Email delivery is not implemented; the application sends only in-app notifications.

## Stage 5 frontend

The browser UI is in `backend/src/main/resources/static/`:

- `index.html` provides the application shell and accessible navigation.
- `styles.css` provides the responsive community-library theme.
- `app.js` connects pages and forms to the existing `/api/...` endpoints.

Open `http://localhost:8080/` after starting the backend. The main pages are Overview, Book catalog, My bookshelf, Borrowing & lending, Wishlist, Community forum, Messages, Notifications, and reader profiles. Borrowing & lending keeps active borrowed/lent books separate from completed exchanges; both participants can submit one review per returned loan, and the UI checks persisted review state to hide a submitted form. After submission, it refreshes the reviewed reader's public reviews and trust score. The catalog offers search, genre, availability, language, and condition filters; choosing all availability states queries each supported backend status. Book forms include Open Library ISBN lookup, manual entry, owner editing/status controls, and CSV upload.

Registration and login use `/api/auth/register` and `/api/auth/login`. The browser keeps the returned bearer token and expiry in `sessionStorage` (not persistent `localStorage`), adds it to protected API calls, and clears it on logout or an unauthorized response. `sessionStorage` reduces persistence after closing the tab but is still readable by JavaScript; it is not protection against cross-site scripting. The app does not store passwords after submission. When the backend uses its default random signing key, restarting it invalidates existing tokens; configure `BOOKBRIDGE_TOKEN_SECRET` for stable local sessions.

Spring serves these static files and the REST API from the same origin, so the frontend requires no separate development server, CORS setup, JavaScript framework, or frontend package installation. Public book browsing and profiles are available without signing in; bookshelf, forum, wishlists, loans, messages, and notification actions require an authenticated account.

## SRS requirements checklist

| # | Status | Requirement |
|---|---|---|
| 1 | Done | Create books with ISBN, metadata, cover URL, and manual-entry fields. |
| 2 | Done | Look up ISBN metadata using Open Library; manual entry is available. |
| 3 | Done | Store Excellent, Good, Acceptable, or Worn but Readable condition. |
| 4 | Done | Store and manage Available, Lent Out, or Unavailable status. |
| 5 | Done | Personal bookshelf lists owned books and incoming pending borrow requests. |
| 6 | Done | Validate and bulk-upload CSV book listings. |
| 7 | Done | Browse community books; default results are available books. |
| 8 | Partial | Search title/author/genre and filter status, language, and condition; distance filtering remains. |
| 9 | Done | Browse the distinct genres of available books. |
| 10 | Done | Book details include owner name/location, condition, loan duration, and pickup preferences. |
| 11 | Done | Authenticated users can add, view, and remove wishlisted books; availability transitions generate duplicate-safe notifications. |
| 12 | Done | Borrowers request an available book with a desired start date and loan duration. |
| 13 | Done | Borrow requests include an optional personal message. |
| 14 | Done | Only owners can approve or reject pending requests with an optional response. |
| 15 | Done | Approval records the exchange method, agreed handover date and location, loan dates, and due date. |
| 16 | Done | Approval supports in-person and postal/courier exchange methods. |
| 17 | Done | Borrowers see active borrowed loans and due dates; owners see active lent-out loans. |
| 18 | Partial | Scheduled, duplicate-safe in-app reminders are created and listed for both participants three days before and on the due date; SMTP email is not implemented. |
| 19 | Done | Borrowers request extensions; only owners can approve or reject them. Approval updates the due date. |
| 20 | Done | Either participant can return an active loan; availability is restored, duplicate returns are rejected, and both participants are prompted in-app to review. |
| 21 | Done | After return, both participants may review each other on reliability, communication, and care of books, with an optional comment; duplicate reviews are rejected. |
| 22 | Done | Trust score averages all received ratings and rounds to two decimals; users without reviews score 0.0. |
| 23 | Done | Public profiles show listed books, borrowing/lending histories, and trust score without private authentication data. |
| 24 | Done | Authenticated users can create, browse, edit, and delete forum posts for recommendations, reading lists, and events. |
| 25 | Done | Authenticated users can privately message another user; only conversation participants can access the thread. |
| 26 | Done | Users can list unread/all notifications and mark one or all as read; notifications cover requests, messages, availability, extensions, returns, reviews, and due-date reminders. |

### Proximity limitation

Distance-based filtering is not implemented yet. Books store an optional city or postal code for display, but comparing locations accurately requires a location/geocoding approach. A future implementation can use approximate postal-code/city matching; it should not claim exact distances without coordinates.

## Screenshots

Screenshots can be added here when a demonstration account has been prepared.
