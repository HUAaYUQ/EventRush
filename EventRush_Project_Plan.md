# EventRush: Large-Scale Event Ticketing and Entry Verification System

## 1. Project Positioning

EventRush is a Java backend project designed to benchmark the backend value of a 12306-style ticketing system without directly copying the railway scenario.

It targets high-concurrency ticketing scenarios such as campus concerts, recruitment talks, competitions, sports events, and public lectures. The system provides event publishing, session management, ticket category management, ticket grabbing, order timeout cancellation, payment simulation, electronic ticket generation, entry verification, rate limiting, and pressure testing.

The project's core purpose is to prove solid Java backend capability: concurrency control, Redis usage, MySQL transaction design, order state management, message queue reliability, cache consistency, idempotency, and performance verification.

In interviews, this project should be used as the main project when discussing Java backend fundamentals.

## 2. Why This Project

The railway ticketing project is strong because it contains high-value backend problems:

- High-frequency query traffic
- Hot resource contention
- Inventory consistency
- Order state transitions
- Unpaid order timeout release
- Cache and database consistency
- Message queue reliability
- Rate limiting and traffic peak handling
- Pressure testing and performance comparison

EventRush keeps these core backend problems, but replaces the common 12306 shell with a more natural and less templated event ticketing scenario.

## 3. Gap It Helps Close

Compared with the referenced student, the current gap is not JavaSE, Redis, or MySQL basics. The real gap is the lack of a project that can carry backend interview questions.

This project is used to close the following gaps:

- Lack of a high-concurrency backend project
- Lack of order and inventory consistency scenarios
- Lack of Redis Lua, idempotency, and MQ scenarios
- Lack of pressure testing data and optimization comparison
- Lack of project-based explanations for Redis, MySQL, JUC, and JVM questions

## 4. Core Business Flow

Main user flow:

1. User logs in.
2. User views event list.
3. User enters event detail page.
4. User selects session and ticket category.
5. User starts ticket grabbing.
6. System deducts ticket stock and creates a pending payment order.
7. User completes simulated payment.
8. System generates an electronic ticket.
9. Entry staff verifies the ticket code.
10. Ticket status changes to verified.

Timeout flow:

1. User grabs a ticket successfully.
2. Order remains unpaid.
3. Delayed message is triggered after timeout.
4. Consumer checks current order status.
5. If still pending payment, cancel the order and release stock.
6. If already paid, ignore the message.

## 5. Recommended Tech Stack

Core stack:

- Java 17
- Spring Boot 3
- MySQL
- Redis
- RocketMQ or RabbitMQ
- MyBatis-Plus
- JMeter or Gatling
- Docker Compose

Use a restrained stack first. Do not rush into ShardingSphere, Canal, Sentinel, Hippo4j, or a microservice architecture unless the core system is already solid.

## 6. Core Modules

### 6.1 User Module

- User registration and login
- Basic user profile
- Authentication and authorization

### 6.2 Event Module

- Event creation
- Event list query
- Event detail query
- Event status management

### 6.3 Session and Ticket Category Module

- Event session management
- Ticket category management
- Stock initialization
- Remaining stock query

### 6.4 Ticket Grabbing Module

- Ticket grabbing request
- Redis stock pre-deduction
- Duplicate grabbing prevention
- Fast failure when stock is insufficient
- Order creation after successful stock deduction

### 6.5 Order Module

- Pending payment order
- Paid order
- Canceled order
- Timeout cancellation
- Order status transition validation

### 6.6 Electronic Ticket Module

- Electronic ticket generation after payment
- Ticket code or QR code generation
- Entry verification
- Duplicate verification prevention

### 6.7 Reliability and Performance Module

- Redis Lua atomic stock deduction
- Delayed message for timeout cancellation
- Message consumption idempotency
- Cache consistency strategy
- Rate limiting
- Pressure testing and performance comparison

## 7. Data Model Draft

Suggested core tables:

- user
- event
- event_session
- ticket_category
- ticket_stock
- ticket_order
- electronic_ticket
- verification_record
- message_consume_record

Important design points:

- ticket_order should contain user_id, event_id, session_id, ticket_category_id, order_status, pay_time, cancel_time, and expire_time.
- electronic_ticket should contain order_id, ticket_code, ticket_status, verified_time, and verifier_id.
- message_consume_record can be used to ensure idempotent message consumption.

## 8. Implementation Roadmap

### Stage 1: Business MVP

Goal: Build the full business loop.

Deliverables:

- Event list and detail query
- Ticket category and stock query
- Ticket grabbing order creation
- Payment simulation
- Electronic ticket generation
- Ticket verification

Learning focus:

- Spring Boot project structure
- MySQL table design
- REST API design
- Basic Redis cache
- Order state management

Interview expression:

"I first abstracted the event ticketing business into event, session, ticket category, order, and electronic ticket models, and completed the full loop from ticket grabbing to entry verification."

### Stage 2: High-Concurrency Ticket Grabbing

Goal: Upgrade from CRUD to backend engineering.

Deliverables:

- Redis stock preloading
- Redis Lua stock deduction
- Duplicate grabbing prevention
- Fast failure when stock is insufficient
- MySQL order creation after Redis success
- Basic consistency check between Redis stock and database order count

Learning focus:

- Redis Lua atomicity
- Concurrency safety
- Idempotency
- MySQL transaction
- JUC basics

Interview expression:

"The ticket grabbing interface does not directly deduct stock in MySQL. I preload ticket stock into Redis and use Lua to complete stock checking, stock deduction, and duplicate grabbing prevention in one atomic operation, reducing database pressure and avoiding overselling."

### Stage 3: Order Reliability

Goal: Make order status reliable under abnormal cases.

Deliverables:

- Delayed message for unpaid order timeout
- Timeout cancellation consumer
- Stock release after cancellation
- Idempotent message consumption
- Payment and cancellation conflict control

Learning focus:

- MQ delayed message
- Duplicate message consumption
- Order state machine
- Idempotent consumer design
- Exception compensation

Interview expression:

"After an order is created, a delayed message is sent for timeout cancellation. The consumer does not cancel blindly. It first checks the current order status and only cancels pending payment orders, so a paid order will not be canceled by a delayed message."

### Stage 4: Cache and Rate Limiting

Goal: Reduce query pressure and protect hot interfaces.

Deliverables:

- Hot event detail cache
- Remaining stock cache strategy
- Cache invalidation on stock changes
- User-level rate limiting
- Interface-level rate limiting

Learning focus:

- Cache penetration, breakdown, and avalanche
- Cache consistency
- Redis rate limiting
- Degradation strategy

Interview expression:

"For hot event details and remaining ticket queries, I use Redis cache to reduce database pressure. For stock-changing operations, I avoid relying on stale cache and use Redis stock as the core pre-deduction source."

### Stage 5: Pressure Testing and Review

Goal: Prepare interview-level evidence.

Deliverables:

- JMeter or Gatling test scripts
- Direct database deduction baseline
- Redis Lua optimized version
- QPS comparison
- Average response time comparison
- Error rate analysis

Learning focus:

- Pressure testing method
- Bottleneck analysis
- JVM basic observation
- Thread pool and database connection pool awareness

Interview expression:

"I used pressure testing to compare direct database stock deduction with Redis pre-deduction. After optimization, database write pressure decreased significantly, and failures mainly came from insufficient stock or rate limiting instead of system exceptions."

## 9. Resume Version

Project name:

EventRush Large-Scale Event Ticketing and Entry Verification System

Tech stack:

Java 17, Spring Boot 3, MySQL, Redis, RocketMQ, MyBatis-Plus, JMeter

Resume bullets:

- Designed core models including events, sessions, ticket categories, orders, and electronic tickets, completing the full loop of ticket grabbing, payment simulation, order cancellation, and entry verification.
- Used Redis and Lua scripts to implement atomic stock deduction for hot ticket categories, preventing overselling and duplicate ticket grabbing under high concurrency.
- Implemented unpaid order timeout cancellation based on delayed messages, using order status checks and idempotent consumption to avoid repeated cancellation or stock release.
- Designed cache strategies for hot event details and remaining ticket queries to reduce database pressure during traffic peaks.
- Built pressure testing scripts to compare database stock deduction and Redis pre-deduction solutions, recording throughput, average response time, and error distribution.

## 10. Interview Focus

Use this project when asked about:

- Redis usage
- Redis Lua
- Inventory deduction
- Overselling prevention
- MySQL transaction
- Order status machine
- MQ delayed message
- Duplicate message consumption
- Idempotency
- Cache consistency
- Rate limiting
- Pressure testing
- JUC and JVM project scenarios

## 11. Key Questions To Prepare

- Why not directly deduct stock in MySQL?
- How does Redis Lua prevent overselling?
- What happens if Redis deduction succeeds but order creation fails?
- How do you handle unpaid order timeout cancellation?
- How do you prevent repeated message consumption?
- How do you prevent duplicate payment, cancellation, or verification?
- How do Redis stock and MySQL order data stay consistent?
- What did pressure testing prove?
- Where can JUC knowledge be reflected in this project?
- How would you further optimize the system if traffic increased?
