# Hotel Booking Microservices System

A full-stack hotel booking application built using a microservices architecture. The system consists of independent Spring Boot services, an Angular frontend, Spring Cloud Gateway, PostgreSQL databases, Apache Kafka for asynchronous event-driven communication, and an optional AI assistant powered by Spring AI and Ollama.

## Deployment Options

This project can be deployed in three different ways:

- Docker Compose
- Kubernetes Manifests
- Helm Chart

The AI Chat Service is optional and can use either a local Ollama/Qwen3 model or the Google Gemini API and can be enabled for all deployment methods.

## Architecture

### Services

| Service                | Responsibility                                          |
| ---------------------- | ------------------------------------------------------- |
| API Gateway            | Single entry point for frontend requests                |
| User Service           | Authentication, authorization, and user management      |
| Booking Service        | Booking management and business rules                   |
| Audit Service          | Consumes audit events and stores audit logs             |
| Apache Kafka           | Event streaming platform for asynchronous communication |
| AI Chat Service        | AI assistant with access to system data                 |
| Frontend               | Angular web application                                 |
| PostgreSQL             | Persistent data storage                                 |
| AI Provider (Optional) | Ollama (local) or Google Gemini (cloud)                 |

---

## System Architecture

```text
                                  ┌─────────────────┐
                                  │     Browser     │
                                  └────────┬────────┘
                                           │
                                           ▼
                             ┌──────────────────────────┐
                             │ Angular Frontend (Nginx) │  
                             └────────────┬─────────────┘
                                          │
                                          ▼
                           ┌──────────────────────────────┐
                           │         API Gateway          │
                           │     Spring Cloud Gateway     │
                           └─┬────────────┬─────────────┬─┘
                             │            │             │
                             ▼            ▼             ▼
                ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
                │ User Service  │ │Booking Service│ │AI Chat Service│
                └───────┬───┬───┘ └────┬───┬──────┘ └───────┬───────┘
                        │   │          │   │                │
                        ▼   │          │   ▼                ▼
               ┌──────────────┐   ┌────────────┐   ┌────────────────┐
               │userservice_db│   │ booking_db │   │  AI Provider   │
               └──────────────┘   └────────────┘   └────────────────┘
                            │         │
                            └────┬────┘
                                 ▼
                        ┌────────────────┐
                        │  Apache Kafka  │
                        │  audit-events  │
                        └───────┬────────┘
                                ▼
                        ┌────────────────┐
                        │ Audit Service  │
                        └───────┬────────┘
                                ▼
                        ┌────────────────┐
                        │    audit_db    │
                        └────────────────┘
```

### Request Flow

```text
Browser
   ↓
Frontend (Angular)
   ↓
API Gateway
   ├── User Service
   ├── Booking Service
   └── AI Chat Service
```

Client requests are routed through the API Gateway using REST APIs.

Internal service-to-service communication uses synchronous REST APIs via Spring Cloud OpenFeign when an immediate response is required. Business events (such as user registration, user updates, and booking creation) are published asynchronously to Apache Kafka, where the Audit Service consumes them and persists audit logs.

---

## Service Responsibilities

### User Service

- JWT authentication
- User management
- Role-based access control
- Admin and User roles

### Booking Service

- Room management
- Booking creation
- Booking cancellation
- Availability checks
- Booking validation rules

### Audit Service

- Consumes audit events from Apache Kafka
- Stores immutable audit logs
- Tracks important business events
- Idempotent event processing
- Passive service without business logic

Examples:

- User registered
- User updated
- User deleted
- Booking created
- Booking cancelled

### AI Chat Service

- Optional AI assistant for administrators
- Built with Spring AI
- Provides read-only access to users, bookings, and audit events
- Uses tool calling to retrieve information from backend services
- Supports two AI providers:
  - Ollama with Qwen3 for local AI processing
  - Google Gemini API for cloud-based AI processing

---

## Architecture Principles

- Separation of concerns
- Independent services
- Clear service boundaries
- API Gateway pattern
- REST for synchronous client requests
- Event-driven communication using Apache Kafka
- AI service is read-only
- AI provider can be switched between Ollama and Google Gemini
- Audit service is passive

---

## Frontend

Built with Angular and Angular Material.

### Features

- User authentication
- Room browsing
- Booking management
- Admin dashboard
- User administration
- AI chat interface (optional)
- Route guards
- Role-based UI
- JWT authentication

---

## Backend Features

### Authentication

- JWT-based authentication
- Stateless security
- Role-based authorization

### Booking Rules

- No bookings in the past
- Check-out date must be after check-in date
- Maximum booking period validation
- Availability checking

### Audit Logging

- Booking events
- User actions
- System activity tracking

### Event Streaming

- Apache Kafka messaging
- Event-driven architecture
- Asynchronous audit processing
- Kafka producers and consumers
- Consumer groups
- Idempotent event handling

---

## Database Design

The system uses a PostgreSQL instance containing multiple databases:

- userservice_db
- booking_db
- audit_db

Each service owns its own database and is responsible for its own data.

---

## Technology Stack

### Frontend

- Angular
- Angular Material
- TypeScript

### Backend

- Spring Boot
- Spring Security
- Spring Data JPA
- Spring Cloud Gateway
- Spring Cloud OpenFeign
- Spring for Apache Kafka
- JWT Authentication
- Spring AI

### Database

- PostgreSQL

### Messaging

- Apache Kafka

### AI

- Spring AI
- Ollama + Qwen3 (local provider)
- Google Gemini API + Gemini 3.5 Flash (cloud provider)

### DevOps

- Docker
- Docker Compose
- Kubernetes
- Helm
- NGINX Ingress Controller
- Minikube
- Maven
- Git
- GitHub

---

## Getting Started

### Prerequisites

Install the following tools before running the project:

- Java 21
- Maven
- Docker
- Git

For Kubernetes deployments:

- kubectl
- Minikube
- Helm

### Build Backend Services

Before building Docker images or deploying the application, build each Spring Boot service:

```bash
cd user-service && ./mvnw clean package

cd ../booking-service && ./mvnw clean package

cd ../audit-service && ./mvnw clean package

cd ../api-gateway && ./mvnw clean package

cd ../ai-chat-service && ./mvnw clean package
```

Alternatively, if you use an IDE such as IntelliJ IDEA, you can build each service directly from the IDE.

### Deployment

For Kubernetes and Helm deployments, build the Docker images inside Minikube before deploying.

| Deployment                 | Command                                                              |
| -------------------------- | -------------------------------------------------------------------- |
| Docker Compose             | `docker compose up --build`                                          |
| Docker Compose + Ollama    | `docker compose --profile ollama up --build`                         |
| Docker Compose + Gemini    | `docker compose --profile gemini up --build`                         |
| Kubernetes                 | `./scripts/build-images.sh` then `./scripts/deploy-k8s.sh`           |
| Kubernetes + Ollama        | `./scripts/build-images.sh` then `./scripts/deploy-k8s-ollama.sh`    |
| Kubernetes + Gemini        | `./scripts/build-images.sh` then `./scripts/deploy-k8s-gemini.sh`    |
| Helm                       | `./scripts/build-images.sh` then `./scripts/deploy-helm.sh`          |
| Helm + Ollama              | `./scripts/build-images.sh` then `./scripts/deploy-helm.sh --ollama` |
| Helm + Gemini              | `./scripts/build-images.sh` then `./scripts/deploy-helm.sh --gemini` |

#### Gemini API Key

Gemini deployments require a GEMINI_API_KEY.

Create a .env file in the project root:

```text
GEMINI_API_KEY=your_api_key
```
Docker Compose reads GEMINI_API_KEY from .env when running the Gemini profile.

The Kubernetes and Helm Gemini deployment scripts read the same .env file and create/update the Kubernetes gemini-secret.

#### Cleanup

| Cleanup                 | Command                     |
| ----------------------- | --------------------------- |
| Kubernetes Manifests    | `./scripts/cleanup.sh k8s`  |
| Helm Chart              | `./scripts/cleanup.sh helm` |
| Both                    | `./scripts/cleanup.sh all`  |

The cleanup script removes Kubernetes resources created by the selected deployment method, including the Gemini Kubernetes Secret. Docker Compose can be stopped with `docker compose down`.

#### Accessing the Application - Docker Compose

| Component   | URL                   |
| ----------- | --------------------- |
| Frontend    | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Kafka UI    | http://localhost:8085 |

#### Accessing the Application - Kubernetes / Helm

Enable the NGINX Ingress Controller:

```bash
minikube addons enable ingress
```

Add the Minikube IP to your hosts file:

```text
<MINIKUBE_IP> hotel.local
<MINIKUBE_IP> kafka.hotel.local
```

Replace <MINIKUBE_IP> with the output of:

```bash
minikube ip
```

Application URLs:

| Component   | URL                      |
| ----------- | ------------------------ |
| Frontend    | http://hotel.local       |
| API Gateway | http://hotel.local/api   |
| Kafka UI    | http://kafka.hotel.local |

#### Verify the Deployment

```bash
kubectl get pods
kubectl get ingress
helm list
```

## Default Credentials

### Administrator

Username:

```text
admin
```

Password:

```text
admin123
```

### Sample Users

All users loaded from data.sql:

Password:

```text
password
```

---

## Key Concepts Demonstrated

- Microservices architecture
- API Gateway pattern (Spring Cloud Gateway)
- Database-per-service design
- RESTful APIs
- Synchronous service-to-service communication with Spring Cloud OpenFeign
- Event-driven architecture with Apache Kafka
- Asynchronous messaging
- Kafka producers and consumers
- Consumer groups
- Idempotent event processing
- JWT authentication and authorization
- Role-based access control (RBAC)
- Docker containerization
- Optional AI integration with Spring AI
- AI provider integration with Ollama/Qwen3 or Google Gemini
- AI tool calling for accessing backend data
- Read-only AI access to business data
- Kubernetes Deployments
- StatefulSets
- ConfigMaps
- Secrets
- Kubernetes Services
- Persistent Volumes
- Kubernetes Jobs
- NGINX Ingress
- Helm charts
- Infrastructure as Code

