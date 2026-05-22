# MoonWalk Restaurant - Backend Architecture & Documentation

## 1. System Architecture & Core Flow

The system is designed with decoupled services to separate front-of-house operations (Lobby, Waitlist) from back-of-house operations (Kitchen Dispatching). To satisfy the requirement of offering this as a solution for *other* restaurants (Part 2), it implements a **Multi-Tenant Architecture** utilizing a shared-database, isolated-schema approach via a `restaurantId` discriminator.

### Event-Driven Choreography

To maintain loose coupling and high availability, the system avoids synchronous REST calls between domains. It utilizes an **Event-Driven Choreography** pattern powered by Apache Kafka (`moonwalk.kitchen.events`).

* **`kitchen-service-group`:** Listens for newly queued dishes. It triggers the dispatcher to attempt chef assignment.
* **`order-service-group`:** Acts as the state aggregator. It listens for `assigned` or `isReady` updates from the kitchen, updates the `OrderExecutionLog`, and manages course sequencing.

---

## 2. Business Use Cases & System Flows

### Lobby & Table Management

The `TableManagerService` and `WaitlistManagerService` handle real-time occupancy and waitlist routing with specific edge-case handling:

1. **Capacity Rejection:** If a walk-in party is larger than the restaurant's maximum table capacity, the system immediately rejects the request with a polite notice, preventing impossible queueing.
2. **Waitlist Routing:** If the lobby is full (all tables occupied), the customer is pushed to a thread-safe, in-memory waitlist. The system calculates an ETA based on the current dining room load.
3. **Smart Table Allocation (Best-Fit over FIFO):** When a table becomes available, the system does *not* blindly pull the oldest waitlist entry. It iterates through the queue to find a `partySize` that best matches the newly freed table's capacity (e.g., a party of 4 gets priority for a 4-seater over a party of 2 who arrived earlier).

### Course-Sequenced Order Execution

The system strictly enforces the chronological flow of a dine-in meal (Appetizers -> Mains -> Desserts).

1. **Parallel Course Processing:** When an order is placed, all items with `courseSequence = 1` (e.g., Appetizers) are published to the kitchen concurrently. If 3 Appetizers are ordered, up to 3 free chefs will begin preparing them simultaneously.
2. **Strict Sequence Gating:** Items with `courseSequence = 2` (Mains) are *not* assigned to chefs, even if chefs are free. The system waits until *all* Appetizers are marked `READY_FOR_SERVE`.
3. **Simulated Dining:** Once a course is fully ready, the `OrderOrchestratorService` triggers an automated "eating phase." Only after the customers finish eating the current course does the system dispatch the next course to the kitchen queue.

---

## 3. ETA Calculation Strategies

Calculating accurate ETAs in a live kitchen is a dynamic scheduling problem. The system uses the **Strategy Pattern** to swap algorithms without modifying core logic.

### Strategy A: `KitchenCourseEstimationStrategy` (Resource-Aware)

Calculates the exact time a specific dish will take by simulating the kitchen's current load and resource availability.

* **Flow:** It utilizes a **Min-Heap (`PriorityQueue`)** to project the timelines of all active `KitchenResource` entities. It iterates through the current `QUEUED` backlog, popping the earliest available chef from the Min-Heap, adding the dish's prep time to that chef's timeline, and pushing the chef back onto the heap.
* **Pros:** Performs true load-balancing estimation (Job-Shop Scheduling) rather than just summing prep times.
* **Cons:** Recalculates the queue state in memory. Mitigation: `OrderItem` is indexed on `restaurant_id` and `status` to ensure database queries remain blazing fast at scale.

### Strategy B: `TableTurnoverEstimationStrategy` (Course-Aware)

Calculates when a table will be freed, which is critical for providing waitlist ETAs.

* **Flow:** Groups all pending items by their `courseSequence`. For each course, it finds the maximum `basePrepTime` (the slowest item). It then applies an `EATING_TIME_MULTIPLIER` to simulate how long the customer will take to consume that course before the next begins.
* **Pros:** Spacing out ETAs by course sequence reflects realistic dine-in behavior.
* **Cons:** Eating time is a heuristic multiplier; real-world variance in human eating speeds will cause slight ETA fluctuations.

---

## 4. Concurrency & Resilience

### Handling Multi-Concurrency (Optimistic Locking)

In a high-throughput kitchen, multiple threads (Kafka consumers, cron jobs) will attempt to assign dishes to chefs simultaneously.

* **The Scenario:** Thread A and Thread B both fetch `OrderItem #12` and see it is `QUEUED`. Thread A assigns it to Chef X. A millisecond later, Thread B tries to assign it to Chef Y.
* **The Solution:** The system uses **Optimistic Locking (`@Version`)** on `KitchenResource` and `OrderItem` entities. When Thread B attempts its update, Hibernate detects the version mismatch and throws an `ObjectOptimisticLockingFailureException`. The `KitchenDispatcherService` catches this gracefully, ignores the collision, and Thread B moves on to the next available item. This avoids heavy, performance-killing pessimistic database locks.

### Self-Healing & Recovery Scheduler

Kitchen operations are simulated asynchronously. If the server crashes, restarts, or a Virtual Thread dies, dishes could be permanently stranded in a `PREPARING` state.

* **The `KitchenRecoveryService`:** A scheduled CRON job runs every 30 seconds. It sweeps the database for dishes that have been `PREPARING` longer than a safety threshold. It automatically "heals" them by marking them `READY_FOR_SERVE`, freeing the assigned chef, and kickstarting the dispatcher to process the backlog.

### Virtual Threads (Project Loom)

The `CookingSimulatorService` uses `Thread.sleep()` to mimic real-world cooking delays. In a traditional JVM, maintaining 10,000 sleeping threads would exhaust the OS thread pool and crash the application. By utilizing **Java 21 Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor()`), the system handles thousands of concurrent sleeping tasks with virtually zero CPU or RAM overhead.

---

## 5. Domain Models & Auditing

* **`Order` & `OrderItem`:** Tracks customer requests, broken down into individual dishes to manage `courseSequence`.
* **`KitchenResource`:** Represents a processing unit (Chef/Oven) managed via versioned optimistic locks.
* **`OrderExecutionLog`:** Strictly satisfies the assignment's auditing requirements. Hooked via `@PrePersist`, it captures immutable snapshots per state change, recording: `timeEstimated`, `timeElapsed`, `activeWorkersCount`, `queueBacklogCount`, and the `algorithmChosen`.

---

## 6. Setup & Execution Instructions

### Prerequisites

* **Java 21+** (Strictly required for Virtual Threads support)
* **Apache Kafka** (Running locally on default port `9092`)
* **Maven** or **Gradle**

### Database

The application utilizes an in-memory **HSQLDB**. No external database setup is required. The schema is automatically generated via Hibernate on startup.

### Running the Application

1. Ensure Kafka is running locally:
```bash
docker run -p 9092:9092 apache/kafka:latest

```


2. Start the Spring Boot application:
```bash
./mvnw spring-boot:run

```

### Testing the Flow

1. **Trigger a Walk-In:** `POST /api/v1/lobby/restaurants/1/walk-in?customerName=John&partySize=2`
2. **Place an Order:** `POST /api/v1/orders` (Pass the `tableId` received from the walk-in).
3. **Monitor the Logs:** The console will output the choreography: assigning chefs, starting the Virtual Thread cooking simulation, completing dishes, and triggering the automated eating cycles.
