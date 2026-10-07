# be-interview-prep

Five Spring Boot features built for the Backend Interview Prep Assignment. Each one was shipped as its own pull request.

| # | Question | PR link |
|---|----------|---------|
| 1 | Task Manager API | |
| 2 | URL Shortener | |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

**Video:** _coming soon_

## Tech stack
Java 21 · Spring Boot 3.5 · Spring Web, Validation, Data JPA · Flyway · H2 (in-memory) · JUnit 5, Mockito, MockMvc

## Prerequisites
- JDK 17 or newer (21 recommended) on `JAVA_HOME`
- No Maven install needed. The Maven wrapper (`mvnw`) is included.

## Run the app
```bash
./mvnw spring-boot:run          # macOS / Linux / Git Bash
mvnw.cmd spring-boot:run        # Windows cmd / PowerShell
```
The app starts on http://localhost:8080 with an in-memory H2 database. Flyway creates the schema on startup.

## Run the tests
```bash
./mvnw verify
```

## Project layout
```
src/main/java/org/example
├── common/      shared error handling (one JSON error format for every endpoint)
├── task/        Q1 Task Manager API
├── shortener/   Q2 URL Shortener
├── auth/, user/ Q3 Authentication & Roles
├── product/     Q4 Product Catalog
└── order/       Q5 Order Service
docs/questions/  the spec and design notes for each question
```
