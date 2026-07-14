Here is the complete execution flow for each use case, tracing every method call from HTTP request to database and back.

---

# Task Manager — Request Flow Documentation

## How to Use This Guide

Start from the top of each use case and follow the numbered steps. Each step shows:
- **What happens** (plain English)
- **Which class/method** is called
- **File location** so you can find it in the project

---

## UC1: Create a Task

**User story:** "As a user, I want to create a new task."

### Via Browser (Thymeleaf)

```
Browser                    Server
  │                          │
  │  1. GET /dashboard       │
  │─────────────────────────▶│
  │                          │ 2. SecurityConfig.filterChain()
  │                          │    → Verifies user has ROLE_USER
  │                          │
  │                          │ 3. WebController.userDashboard()
  │                          │    → Calls taskService.findAllTasksByUser(user)
  │                          │    → Adds tasks, username, isAdmin to Model
  │                          │    → Returns "user/dashboard" view name
  │                          │
  │                          │ 4. Thymeleaf processes user/dashboard.html
  │                          │    → layout:decorate="~{layout}" merges with layout.html
  │                          │    → Renders task list + create form
  │                          │
  │  5. HTML page returned   │
  │◀─────────────────────────│
  │                          │
  │  6. User fills form,     │
  │     clicks "Create Task" │
  │─────────────────────────▶│
  │                          │ 7. SecurityConfig.filterChain()
  │                          │    → Verifies user has ROLE_USER
  │                          │    → Validates CSRF token (auto-added by Thymeleaf)
  │                          │
  │                          │ 8. WebController.createTask()
  │                          │    → Extracts user from @AuthenticationPrincipal
  │                          │      (resolved by CurrentUserArgumentResolver)
  │                          │    → Extracts title, description from @RequestParam
  │                          │    → Calls taskService.createTask(user, title, description)
  │                          │
  │                          │ 9. TaskService.createTask()
  │                          │    → Creates new Task(title, description, user)
  │                          │      → Task constructor sets createdAt = now
  │                          │    → Calls taskRepository.save(task)
  │                          │      → JPA INSERT into tasks table
  │                          │      → Database assigns ID
  │                          │    → Calls auditService.logEvent(CREATED, user, "Task", id, "...")
  │                          │      → AuditService creates AuditEntry
  │                          │      → Calls auditRepository.save(entry)
  │                          │    → Returns saved Task
  │                          │
  │                          │ 10. WebController adds flash message:
  │                          │     "Task created successfully!"
  │                          │     → Redirects to /dashboard (PRG pattern)
  │                          │
  │  11. Browser follows     │
  │      redirect to         │
  │      /dashboard          │
  │◀─────────────────────────│  (Steps 1-5 repeat — task list now includes new task)
```

### Via REST API

```
HTTP Client                Server
  │                          │
  │  POST /api/tasks         │
  │  Authorization: Basic    │
  │  {"title":"...",         │
  │   "description":"..."}   │
  │─────────────────────────▶│
  │                          │ 1. SecurityConfig.filterChain()
  │                          │    → Verifies user has ROLE_USER
  │                          │
  │                          │ 2. TaskController.createTask()
  │                          │    → CurrentUserArgumentResolver gets User from SecurityContext
  │                          │    → @Valid validates CreateTaskRequest (title not blank, etc.)
  │                          │    → Calls taskService.createTask(user, title, description)
  │                          │
  │                          │ 3. TaskService.createTask()
  │                          │    (same flow as browser step 9 above)
  │                          │
  │                          │ 4. TaskController returns:
  │                          │    HTTP 201 Created
  │                          │    ApiResponse.success("Task created", TaskResponse.from(task))
  │                          │
  │  5. JSON response        │
  │◀─────────────────────────│
```

**Files to read in order:**
1. `SecurityConfig.java` — `filterChain()` method
2. `CurrentUserArgumentResolver.java` — `resolveArgument()` method
3. `CreateTaskRequest.java` — validation annotations
4. `TaskController.java` — `createTask()` method
5. `TaskService.java` — `createTask()` method
6. `Task.java` — constructor, fields
7. `TaskRepository.java` — `save()` method (inherited from JpaRepository)
8. `AuditService.java` — `logEvent()` method
9. `AuditEntry.java` — constructor
10. `AuditRepository.java` — `save()` method
11. `TaskResponse.java` — `from()` static factory method
12. `ApiResponse.java` — `success()` static factory method

---

## UC2: Mark Task as Complete

**User story:** "As a user, I want to mark my task as done."

### Sequence Diagram → Code Trace

Every step in the Sequence Diagram maps directly to code:

```
Sequence Diagram Step          |  Java Code
═══════════════════════════════╪═══════════════════════════════════════════
U -> TS : markTaskComplete(101)│  TaskController.markTaskComplete(101L)
                               │  or WebController.markComplete(101L, ...)
                               │
TS -> TR : findById(101)       │  taskRepository.findById(101L)
                               │  → Throws EntityNotFoundException if null
                               │
TR --> TS : Task #101          │  Returns Optional<Task>
                               │
TS -> T : markComplete()       │  task.markComplete()
                               │
T -> T : setComplete(true)     │  this.isComplete = true;  (inside Task)
                               │
opt [notifications enabled]    │  if (owner.isNotificationEnabled()) {
T -> NS : sendNotification()   │      notificationService.sendNotification(task);
                               │  }
                               │
TS -> AS : logEvent(COMPLETED) │  auditService.logEvent(AuditAction.COMPLETED, ...)
                               │
TS -> TR : save(Task #101)     │  taskRepository.save(task)
                               │
TS --> U : "Task completed"    │  return ApiResponse.success("Task marked as complete")
```

**Files to read in order:**
1. `TaskController.java` — `markTaskComplete()` method (REST)
2. `WebController.java` — `markComplete()` method (Browser)
3. `TaskService.java` — `markTaskComplete()` method (THE key method — read carefully)
4. `Task.java` — `markComplete()` domain method (business rule: can't complete twice)
5. `NotificationService.java` — `sendNotification()` method
6. `AuditService.java` — `logEvent()` method
7. `TaskRepository.java` — `findById()` and `save()` methods

---

## UC3: View All Tasks

**User story:** "As a user, I want to see all my tasks."

This is a **read-only** operation — no writes, no audit, no notifications.

```
Sequence Diagram Step          |  Java Code
═══════════════════════════════╪═══════════════════════════════════════════
U -> TS : findAllTasksByUser() │  taskService.findAllTasksByUser(user)
                               │
TS -> TR : findAllByOwner()    │  taskRepository.findAllByOwner(user)
                               │  → @Transactional(readOnly = true)
                               │
TR --> TS : List<Task>         │  Returns List<Task> (or empty list)
                               │
TS --> U : TaskResponse list   │  Stream.map(TaskResponse::from).toList()
```

**Files to read in order:**
1. `TaskController.java` — `viewAllTasks()` method (REST)
2. `WebController.java` — `userDashboard()` method (Browser)
3. `TaskService.java` — `findAllTasksByUser()` method
4. `TaskRepository.java` — `findAllByOwner()` method (Spring Data query derivation)
5. `TaskResponse.java` — `from()` static factory method (entity → DTO conversion)

---

## UC4: Delete a Task (Admin Only)

**User story:** "As an admin, I want to delete any task."

### Authorization Flow

```
Browser/Client               Server
  │                          │
  │  DELETE /api/admin/tasks/5│
  │─────────────────────────▶│
  │                          │ 1. SecurityConfig.filterChain()
  │                          │    → requestMatchers("/api/admin/**").hasRole("ADMIN")
  │                          │    → If user has ROLE_USER only → 403 Forbidden
  │                          │    → If user has ROLE_ADMIN → proceed
  │                          │
  │                          │ 2. AdminController.deleteTask(5L)
  │                          │    → Calls taskService.deleteTask(5L)
  │                          │
  │                          │ 3. TaskService.deleteTask()
  │                          │    → taskRepository.findById(5L)
  │                          │    → auditService.logEvent(DELETED, ...)  ← BEFORE delete!
  │                          │    → taskRepository.delete(task)
  │                          │      → JPA DELETE FROM tasks WHERE id = 5
```

**Files to read in order:**
1. `SecurityConfig.java` — `filterChain()` method (authorization rules)
2. `User.java` — `getAuthorities()` method (how roles become Spring Security authorities)
3. `UserDetailsServiceImpl.java` — `loadUserByUsername()` method
4. `AdminController.java` — `deleteTask()` method
5. `TaskService.java` — `deleteTask()` method
6. `TaskRepository.java` — `delete()` method (inherited from JpaRepository)

---

## UC5: Assign Task to User

**User story:** "As a user, I want to assign a task to another user."

### Sequence Diagram → Code Trace

```
Sequence Diagram Step          |  Java Code
═══════════════════════════════╪═══════════════════════════════════════════
Admin -> TS : assign(102, 42)  │  TaskController.assignTaskToUser(request)
                               │  or WebController.assignTask(id, userId, ...)
                               │
TS -> TR : findById(102)       │  taskRepository.findById(102L)
                               │
TR --> TS : Task #102          │
                               │
TS -> UR : findById(42)        │  userRepository.findById(42L)
                               │
UR --> TS : User #42           │
                               │
alt [both found]               │  (implicit — exceptions thrown if not found)
                               │
TS -> T : setAssignedTo(#42)   │  task.setAssignedTo(assignee)
                               │
T -> T : this.assignedTo = #42 │  this.assignedTo = user;  (inside Task)
                               │
TS -> AS : logEvent(ASSIGNED)  │  auditService.logEvent(AuditAction.ASSIGNED, ...)
                               │
opt [notifications enabled]    │  if (assignee.isNotificationEnabled()) {
TS -> NS : sendNotification()  │      notificationService.sendNotification(task);
                               │  }
                               │
TS -> TR : save(Task #102)     │  taskRepository.save(task)
                               │
TS --> Admin : "Task assigned" │  return ApiResponse.success("Task assigned successfully")
```

**Files to read in order:**
1. `TaskController.java` — `assignTaskToUser()` method (REST)
2. `WebController.java` — `assignTask()` method (Browser)
3. `AssignTaskRequest.java` — validation annotations
4. `TaskService.java` — `assignTaskToUser()` method (THE key method)
5. `Task.java` — `setAssignedTo()` domain method
6. `UserRepository.java` — `findById()` method (inherited from JpaRepository)
7. `TaskRepository.java` — `findById()` and `save()` methods
8. `AuditService.java` — `logEvent()` method
9. `NotificationService.java` — `sendNotification()` method

---

## Authentication Flow (Login)

**User story:** "As a user, I want to log in."

```
Browser                       Server
  │                          │
  │  GET /login              │
  │─────────────────────────▶│
  │                          │ 1. SecurityConfig permits /login to all
  │                          │ 2. WebController.loginPage()
  │                          │    → Returns "login" view name
  │                          │ 3. Thymeleaf renders login.html
  │                          │
  │  HTML login form         │
  │◀─────────────────────────│
  │                          │
  │  POST /login             │
  │  username=alice          │
  │  password=password123    │
  │─────────────────────────▶│
  │                          │ 4. UsernamePasswordAuthenticationFilter
  │                          │    (Spring Security — not our code)
  │                          │    → Extracts username/password
  │                          │
  │                          │ 5. UserDetailsServiceImpl.loadUserByUsername("alice")
  │                          │    → userRepository.findByUsername("alice")
  │                          │    → Returns User entity (implements UserDetails)
  │                          │
  │                          │ 6. DaoAuthenticationProvider (Spring Security)
  │                          │    → BCryptPasswordEncoder.matches("password123", storedHash)
  │                          │    → If match: creates Authentication token
  │                          │    → Stores in SecurityContextHolder
  │                          │
  │                          │ 7. Redirect to /dashboard
  │                          │
  │  302 /dashboard          │
  │◀─────────────────────────│
  │                          │
  │  GET /dashboard          │
  │─────────────────────────▶│
  │                          │ 8. CurrentUserArgumentResolver
  │                          │    → Gets User from SecurityContext
  │                          │    → Injects into @AuthenticationPrincipal
  │                          │
  │                          │ 9. WebController.userDashboard()
  │                          │    → Shows tasks for authenticated user
```

**Files to read in order:**
1. `SecurityConfig.java` — `filterChain()` method (login page, form login config)
2. `login.html` — the login form
3. `WebController.java` — `loginPage()` method
4. `UserDetailsServiceImpl.java` — `loadUserByUsername()` method
5. `User.java` — `UserDetails` interface implementation (`getAuthorities()`, `getPassword()`, etc.)
6. `UserRepository.java` — `findByUsername()` method
7. `CurrentUserArgumentResolver.java` — `resolveArgument()` method
8. `SecurityConfig.java` — `passwordEncoder()` bean

---

## Spring Security Filter Chain (Every Request)

**This runs BEFORE any controller method is called.**

```
HTTP Request
    │
    ▼
1. SecurityContextPersistenceFilter
   → Restores SecurityContext from HTTP session (if exists)
    │
    ▼
2. CsrfFilter
   → Validates CSRF token for POST/PUT/PATCH/DELETE requests
   → Skips for GET requests
    │
    ▼
3. UsernamePasswordAuthenticationFilter
   → Only fires on POST /login
   → Calls UserDetailsServiceImpl.loadUserByUsername()
   → Validates password with BCryptPasswordEncoder
    │
    ▼
4. AuthorizationFilter
   → Checks: does the authenticated user have the required role?
   → Matches URL against rules in SecurityConfig.filterChain()
   → /api/admin/** → hasRole("ADMIN")
   → /api/tasks/** → hasRole("USER")
   → /dashboard    → hasRole("USER")
   → /login        → permitAll()
    │
    ▼
5. ExceptionTranslationFilter
   → Converts AccessDeniedException → 403 Forbidden
   → Converts AuthenticationException → 401 Unauthorized (API) or redirect to /login (Browser)
    │
    ▼
Controller method executes
```

**Files to read:**
1. `SecurityConfig.java` — the entire class (it configures this entire chain)

---

## Recommended Reading Order (Learning Path)

If you're studying this project from scratch, read in this order:

| Order | File | Why First |
| :--- | :--- | :--- |
| 1 | `TaskManagerApplication.java` | Entry point — see how Spring Boot starts |
| 2 | `domain/User.java` | Core entity — understand the user model |
| 3 | `domain/Task.java` | Core entity — understand the task model |
| 4 | `domain/UserRole.java` | Simple enum — roles and permissions |
| 5 | `domain/AuditAction.java` | Simple enum — what gets audited |
| 6 | `domain/AuditEntry.java` | Entity — how audit logging works |
| 7 | `domain/Notification.java` | Entity — how notifications are stored |
| 8 | `repository/UserRepository.java` | How users are queried |
| 9 | `repository/TaskRepository.java` | How tasks are queried |
| 10 | `repository/AuditRepository.java` | How audit entries are queried |
| 11 | `config/SecurityConfig.java` | THE most important config — authentication + authorization |
| 12 | `service/UserDetailsServiceImpl.java` | Bridge between Spring Security and our User entity |
| 13 | `config/CurrentUserArgumentResolver.java` | How @AuthenticationPrincipal works |
| 14 | `config/WebMvcConfig.java` | How the resolver is registered |
| 15 | `service/AuditService.java` | Simple service — good introduction to service layer |
| 16 | `service/NotificationService.java` | Simple service — notification delivery |
| 17 | `service/TaskService.java` | THE core service — all business logic (read carefully) |
| 18 | `controller/GlobalExceptionHandler.java` | How errors become JSON responses |
| 19 | `controller/TaskController.java` | REST API endpoints |
| 20 | `controller/AdminController.java` | Admin REST endpoints |
| 21 | `controller/WebController.java` | Thymeleaf HTML page endpoints |
| 22 | `dto/CreateTaskRequest.java` | Input validation for UC1 |
| 23 | `dto/AssignTaskRequest.java` | Input validation for UC5 |
| 24 | `dto/TaskResponse.java` | Entity → DTO conversion |
| 25 | `dto/ApiResponse.java` | Standardized JSON response wrapper |
| 26 | `config/DataInitializer.java` | Demo data for development |
| 27 | `templates/layout.html` | Master page template |
| 28 | `templates/login.html` | Login form |
| 29 | `templates/user/dashboard.html` | User dashboard |
| 30 | `templates/admin/dashboard.html` | Admin dashboard |
