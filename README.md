# 📋 Task Manager

A full-stack task management application with a **Java 21 Spring Boot 3 REST API backend** 
with **PostgreSQL/H2** and a **vanilla JavaScript SPA frontend** in separated architecture.

The back end is designed from UML diagrams, built with Spring Security, and deployable via 
Docker.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-75%20passed-success)]()
---

## Features

- **Five core use cases:** Create, Complete, View, Delete, and Assign tasks
- **User management:** Registration, role changes, user deletion (admin)
- **Role-based access:** Regular users + administrators with permission hierarchy
- **Dual interface:** Backend REST API (JSON) + Frontend Single Page Application (HTML/CSS/JS)
- **Pagination & filtering:** Task lists with configurable page size, status filter, and sorting
- **Audit logging:** Immutable, append-only log of all system actions
- **Two deployment profiles:** Dev (H2 in-memory) and Prod (PostgreSQL with external secrets)
- **75+ automated tests:** Unit, integration, repository, and controller tests
---

## Architecture

The application follows a strict **layered architecture** designed from PlantUML diagrams:

```
Presentation Layer  (REST Controllers)
       │
Service Layer       (Business Logic + Orchestration)
       │
Repository Layer    (Spring Data JPA)
       │
Domain Layer        (JPA Entities + Business Rules)
```

The presentation layer use Single Page App Frontend with Vanilla HTML/CSS/JavaScript.

```
┌────────────────────────────────────────────┐
│ SPA Frontend (HTML/CSS/JS)                 │
│ index.html, dashboard.html, admin.html     │
├────────────────────────────────────────────┤
│ REST API (Spring Boot)                     │
│ TaskController, AdminController,           │
│ UserController, GlobalExceptionHandler     │
├────────────────────────────────────────────┤
│ Service Layer                              │
│ TaskService, AuditService,                 │
│ NotificationService, UserDetailsService    │
├────────────────────────────────────────────┤
│ Repository Layer (Spring Data JPA)         │
│ TaskRepository, UserRepository,            │
│ AuditRepository                            │
├────────────────────────────────────────────┤
│ Domain Layer (JPA Entities)                │
│ User, Task, Notification, AuditEntry       │
└────────────────────────────────────────────┘
```

The front end and back end are developed realistically in separated architecture, preparing 
for production environment. 

```
Development:
┌─────────────────────┐     ┌──────────────────────┐
│  Front end (Vite)   │────▶│  Back end (Spring)   │
│  localhost:3000     │     │  localhost:8080      │
│  npm run dev        │     │  mvn spring-boot:run │
└─────────────────────┘     └──────────────────────┘

Production:
┌─────────────────────┐     ┌──────────────────────┐
│  CDN / Nginx        │────▶│  Java Server         │
│  app.yourdomain.com │     │  api.yourdomain.com  │
│  Static files       │     │  Spring Boot JAR     │
└─────────────────────┘     └──────────────────────┘
```

### UML Diagrams

| Diagram  | Description                           |
|:---------|:--------------------------------------|
| Use Case | Actors and their goals (10 use cases) |
| Class    | Static structure with relationships   |
| Sequence | Dynamic flow for each use case        |

---

## Quick Start

### Development

#### Prerequisites

- Java 21 or higher
- Maven 3.9+ (wrapper included)

#### Run in Development Mode (H2 Database)

```bash
# Clone the repository
git clone https://github.com/lussac7/task-manager.git
cd task-manager

# Run with dev profile (H2 in-memory, demo data)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:3000/login` (SPA front-end port) and log in with:

| Username | Password | Role |
| :--- | :--- | :--- |
| `alice` | `password123` | USER |
| `bob` | `password123` | USER |
| `admin` | `admin123` | ADMIN |

### Production Deployment

#### Prerequisites
- PostgreSQL 14+ or MySQL 8+
- Java 21
- Maven 3.9+

#### Step 1: Set Up Database

##### Option A: PostgreSQL
```bash
# 1. Set your database password as environment variable
export TASKDB_PASS="your_secure_password"

# 2. Run the setup script
sudo -u postgres psql << EOF
SELECT 'CREATE DATABASE taskdb
    WITH ENCODING = ''UTF8''
    LC_COLLATE = ''pt_BR.utf8''
    LC_CTYPE = ''pt_BR.utf8''
    TEMPLATE template0;'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'taskdb')
\gexec

SELECT 'CREATE USER taskuser WITH PASSWORD ''${TASKDB_PASS}'';'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'taskuser')
\gexec

ALTER DATABASE taskdb owner to taskuser;
ALTER SCHEMA public OWNER TO taskuser;
GRANT ALL PRIVILEGES ON DATABASE taskdb TO taskuser;
GRANT ALL ON SCHEMA public TO taskuser;
EOF
```

##### Option B: MySQL
```bash
mysql -u root -p -e "
  CREATE DATABASE IF NOT EXISTS taskdb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
  
  CREATE USER IF NOT EXISTS 'taskuser'@'localhost'
    IDENTIFIED BY '${TASKDB_PASS}';
  
  GRANT ALL PRIVILEGES ON taskdb.* TO 'taskuser'@'localhost';
  FLUSH PRIVILEGES;
"
```

#### Step 2: Configure Secrets

```bash
# Create the secrets directory
sudo mkdir -p /etc/task-manager

# Add database credentials
sudo tee /etc/task-manager/secrets.properties << EOF
db.username=taskuser
db.password=${TASKDB_PASS}
EOF

# Secure the file
sudo chmod 600 /etc/task-manager/secrets.properties
```

#### Step 3: Run in Production

```bash
# Set the frontend URL (if you have one)
export FRONTEND_URL="https://your-frontend-domain.com"

# Run the application
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

#### Step 4: Build for Production

```bash
# Build the JAR file
mvn clean package -DskipTests

# Run the JAR
SPRING_PROFILES_ACTIVE=prod java -jar target/task-manager-0.0.1-SNAPSHOT.jar
```

#### Security Notes
- **Never commit secrets to version control**
- Use environment variables or a secrets manager in production
- Change default passwords immediately
- Use HTTPS in production
- Regularly update dependencies

---

## API Endpoints

### Authentication

The SPA uses session-based auth (form login). For REST clients, use HTTP Basic Auth.

### Tasks (User)

| Method  | Endpoint                                    | Description           |
|:--------|:--------------------------------------------|:----------------------|
| `GET`   | `/api/tasks?page=0&size=10&sort=createdAt,desc&complete=false` | List user's tasks |
| `POST`  | `/api/tasks`                                | Create a new task     |
| `PATCH` | `/api/tasks/{id}/complete`                  | Mark task as complete |
| `PATCH` | `/api/tasks/assign`                         | Assign task to a user |

### Tasks (Admin)

| Method   | Endpoint                                         | Description                |
|:---------|:-------------------------------------------------|:---------------------------|
| `GET`    | `/api/admin/tasks?page=0&size=10&complete=false` | List ALL tasks (paginated) |
| `DELETE` | `/api/admin/tasks/{id}`                          | Delete a task              |

### Users
| Method   | Endpoint                          | Access  | Description          |
|:---------|:----------------------------------|:--------|:---------------------|
| `POST`   | `/api/users/register`             | Public  | Register new account |
| `GET`    | `/api/users`                      | Admin   | List all users       |
| `POST`   | `/api/users/`                     | Admin   | Create user          |
| `PATCH`  | `/api/users/{id}/role?role=ADMIN` | Admin   | Change user role     |
| `DELETE` | `/api/users/{id}`                 | Admin   | Delete user          |

### Pagination

All list endpoints support pagination:

```
GET /api/tasks?page=0&size=10&sort=createdAt,desc&complete=false
```

Response includes metadata:

```json
{
  "data": {
    "content": [ "..." ],
    "page": 0,
    "size": 10,
    "totalElements": 47,
    "totalPages": 5,
    "first": true,
    "last": false
  }
}
```

---

## Running Tests

```bash
# All tests (75 tests)
mvn test -Dspring.profiles.active=dev

# Unit tests only (fast, no database)
mvn test -Dtest='!*IntegrationTest'

# Integration tests only
mvn test -Dtest='*IntegrationTest'
```

---

## Docker

```bash
# Build and run with PostgreSQL
docker-compose up -d

# Dev mode with H2
docker-compose -f docker-compose-dev.yml up -d

# Stop
docker-compose down
```

---

## Project Structure

The back-end structure uses port 8080 and is as follows:

```
src/
├── main/java/io.github.lussac7/taskmanager/
│   ├── config/          # Security, MVC, OpenAPI config
│   ├── controller/      # REST controllers
│   ├── domain/          # JPA entities, enums
│   ├── dto/             # Data Transfer Objects
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # Business logic
│   └── TaskManagerApplication.java # The Main Class
│
├── main/resources/
│   ├── static/          # SPA frontend (HTML/CSS/JS) (only if using integrated architecture)
│   ├── application.yml  # Dev configuration
│   └── application-prod.yml  # Production configuration
└── test/...             # Unit + integration tests
```

Also, the full-stack project adding the front-end port 3000 has the structure bellow:

```
/projects/
├── java/task-manager/                      ← Spring Boot Backend
│   └── src/
│       ├── main/java/io.github.lussac7/taskmanager/
│       │   └──...
│       ├── main/resources/
│       │   └──...
│       └── test/...
│
└── javascript/task-manager-frontend/       ← Vanilla JS Frontend
    ├── index.html
    ├── register.html
    ├── dashboard.html
    ├── admin.html
    ├── admin-users.html
    ├── css/
    │   └── style.css
    ├── js/
    │   ├── api.js
    │   ├── auth.js
    │   ├── dashboard.js
    │   └── admin.js
    ├── vite.config.js
    ├── package.json
    └── node_modules/
```

---

## Built With

- **Spring Boot 3.4** — Application framework
- **Spring Security 6** — Authentication and authorization
- **Spring Data JPA** — Database access
- **PostgreSQL / H2** — Production / Development database
- **Vanilla JavaScript** — SPA frontend (no frameworks)
- **JUnit 5 + Mockito** — Testing
- **Docker** — Containerization
- **PlantUML** — Architecture diagrams
- **Maven** — Build and dependency management

---

## Documentation

- **Javadoc:** Generate with `mvn javadoc:javadoc` (output in `target/reports/apidocs/`)
- **UML Diagrams:** See `docs/diagrams/` folder

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Lussac Prestes Maia**

- GitHub: [@lussac7](https://github.com/lussac7/task-manager)
- LinkedIn: [Lussac P. Maia](https://linkedin.com/in/lussac-maia-eng-software)

Acknowledgments: This project was developed with the assistance of DeepSeek AI for code 
generation and problem-solving. 
All architecture decisions, testing, and final code reviews were performed by me.

---

*Built from UML diagrams to production deployment.*