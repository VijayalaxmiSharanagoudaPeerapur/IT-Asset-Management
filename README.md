# Smart IT Asset & Service Management System

A mini internal IT platform for tracking laptops/desktops, employees, assignments, repair tickets, asset history, and an admin dashboard.

Built with **Java 21, Spring Boot 3, REST APIs, JPA/Hibernate, MySQL, JWT security, and HTML/CSS/JavaScript**.

## What it solves

Without a system, IT teams cannot quickly answer:

- Which laptop is assigned to Rahul?
- When was this laptop repaired?
- Who reported this issue?
- Is Dell-105 available?
- How many laptops are under repair?

This project puts employees, assets, assignments, tickets, repairs, and history on one platform.

## Modules

1. **Authentication & Authorization** — JWT login, `ADMIN` and `EMPLOYEE` roles
2. **Employee Management** — CRUD (soft-delete), search/filter/pagination, linked login account
3. **Asset Management** — inventory, assignment/unassignment, status workflow
4. **Service / Ticket Management** — priorities, status tracking, notifications
5. **Admin Dashboard & Reports** — live counts for assets and tickets

## Business rules (interview-ready)

| Topic | Behavior |
| --- | --- |
| Duplicate assignment | An asset cannot be assigned if it already has an employee. Unassign first. |
| Asset status | `AVAILABLE` → `ASSIGNED` → `REPAIR` / `RETIRED`. Retired assets are unassigned. Repair blocks new assignment. |
| Employee delete | Soft-delete (`active=false`). Blocked while assets are still assigned, so history is preserved. |
| Tickets | `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`. Invalid jumps are rejected. |
| Notifications | Created for admins on new tickets and for reporters/assignees on status changes. |
| Validation | Bean Validation on DTOs + global `@RestControllerAdvice` exception handler. |

## Database relationships

```
UserAccount 1 ── 1 Employee
Employee 1 ── * Asset          (current assignment)
Asset 1 ── * AssetHistory      (lifecycle)
Employee 1 ── * Ticket         (reporter / assignee)
Asset 1 ── * Ticket
UserAccount 1 ── * Notification
```

## Tech stack

- Backend: Spring Boot 3.4, Spring Web, Spring Data JPA, Spring Security
- DB: MySQL 8 (`ddl-auto=update` for local setup)
- Auth: BCrypt passwords + JWT
- API docs: springdoc OpenAPI / Swagger UI
- Frontend: static HTML/CSS/JS served by Spring Boot
- Testing APIs: Postman collection in `/postman`

## Prerequisites

- JDK 21+
- MySQL 8 running locally **or** Docker
- Maven Wrapper is included (`mvnw.cmd`) — no global Maven install required

## Run

### 1. Start MySQL

Docker:

```bash
docker compose up -d
```

Or local MySQL: create database `itasm` (optional; JDBC URL uses `createDatabaseIfNotExist=true`).

Default credentials in `src/main/resources/application.properties`:

```
username=root
password=          (empty — XAMPP default)
```

Change these if your MySQL password is different.

### 2. Start the app (Windows)

```powershell
.\mvnw.cmd spring-boot:run
```

If the wrapper script has trouble on your machine, use Maven 3.9+ from `~/.m2/wrapper` after the first download, or install Maven and run `mvn spring-boot:run`.

### 3. Open

| Resource | URL |
| --- | --- |
| UI login | http://localhost:8080 |
| Swagger / OpenAPI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

### Demo users (seeded on first start)

| Role | Username | Password |
| --- | --- | --- |
| Admin | `admin` | `Admin@123` |
| Employee | `vijay` | `Vijay@123` |
| Employee | `rahul` | `Rahul@123` |

## REST API map

### Auth
- `POST /api/auth/login`
- `GET /api/auth/me`

### Employees (admin writes; employees can view self)
- `GET /api/employees?q=&department=&active=&page=&size=`
- `GET /api/employees/{id}`
- `GET /api/employees/{id}/assets`
- `POST /api/employees`
- `PUT /api/employees/{id}`
- `DELETE /api/employees/{id}` (deactivate)

### Assets
- `GET /api/assets?q=&status=&page=&size=`
- `POST /api/assets`
- `PUT /api/assets/{id}`
- `POST /api/assets/{id}/assign`
- `POST /api/assets/{id}/unassign`
- `POST /api/assets/{id}/status`
- `GET /api/assets/{id}/history`

### Tickets
- `GET /api/tickets?q=&status=&priority=&page=&size=`
- `POST /api/tickets`
- `POST /api/tickets/{id}/assign`
- `POST /api/tickets/{id}/status`

### Dashboard
- `GET /api/dashboard/stats` (admin)
- `GET /api/notifications`
- `POST /api/notifications/{id}/read`

## Postman

Import `postman/ITASM.postman_collection.json`.

1. Run **Login Admin** (it stores `{{token}}`)
2. Call any other request

## Project structure

```
src/main/java/com/itasm/
  controller/     REST endpoints
  service/        business logic
  entity/         JPA entities
  repository/     Spring Data JPA
  dto/            request/response records
  security/       JWT filter + current user
  exception/      global handler
  config/         security, OpenAPI, seed data
src/main/resources/static/   login + dashboard UI
```

## Why Spring Boot and JPA?

Spring Boot gives production-ready REST, security, and configuration with little boilerplate. JPA/Hibernate maps Java entities to MySQL tables so relationships (employee–asset, asset–history, ticket–reporter) stay in one object model instead of handwritten SQL for every join.

## GitHub

Initialize is already done locally. To publish:

```bash
git add .
git commit -m "Add Smart IT Asset and Service Management System"
git remote add origin <your-repo-url>
git push -u origin main
```
