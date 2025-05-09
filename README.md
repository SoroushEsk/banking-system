Banking System

A Spring Boot application for processing banking transactions, including deposits, withdrawals, and balance verification, with robust logging and concurrency handling. The system ensures data consistency using pessimistic locking and provides detailed operation logs for monitoring success, failure, and performance metrics like transactions per second (TPS).

## Features

- **Transaction Processing**: Handles deposits and withdrawals with validation to prevent negative balances.
- **Concurrency Control**: Uses pessimistic locking (`PESSIMISTIC_WRITE`) and retry logic (3 attempts) for withdrawals to manage concurrent transactions safely.
- **Balance Verification**: Scheduled balance checks to detect inconsistencies between stored and calculated balances.
- **Logging**: Structured JSON logs for all operations (e.g., `DEPOSIT`, `WITHDRAWAL`, `BALANCE_CHECK`) with success/failure status, duration, and failure reasons, output to the console via Logback.
- **Testing**: Comprehensive test suites for:
  - **Negative Balance Tests**: Ensures withdrawals exceeding the balance are rejected.
  - **Throughput Tests**: Measures TPS under concurrent load.
- **Dockerized Deployment**: Runs with PostgreSQL using Docker Compose for easy setup and log visibility.

## Technologies

- **Backend**: Spring Boot 3.x, Kotlin 1.9.x
- **Database**: PostgreSQL 15
- **ORM**: Spring Data JPA
- **Logging**: Logback with JSON output
- **Testing**: JUnit 5, Testcontainers, Spring Boot Test, MockMvc
- **Serialization**: Jackson
- **Concurrency**: Kotlin Coroutines (non-suspending with `runBlocking`)
- **Containerization**: Docker, Docker Compose
- **Build Tool**: Maven

## Prerequisites

- **Java**: 21 (e.g., Eclipse Temurin)
- **Maven**: 3.9.6 or later
- **Docker**: Latest version with Docker Compose
- **Git**: For cloning the repository
- **curl**: For testing API endpoints

## Project Structure

```
banking-system/
├── src/
│   ├── main/
│   │   ├── kotlin/com/tabdil_exchange/test_project/
│   │   │   ├── features/
│   │   │   │   ├── account/model/Account.kt
│   │   │   │   ├── account/repository/AccountRepository.kt
│   │   │   │   ├── transaction/controller/TransactionController.kt
│   │   │   │   ├── transaction/model/Transaction.kt
│   │   │   │   ├── transaction/model/dto/TransactionRequest.kt
│   │   │   │   ├── transaction/model/dto/TransactionDepositResponse.kt
│   │   │   │   ├── transaction/model/dto/TransactionWithdrawalResponse.kt
│   │   │   │   ├── transaction/repository/TransactionRepository.kt
│   │   │   │   ├── transaction/service/TransactionService.kt
│   │   │   ├── util/
│   │   │   │   ├── BalanceCheckScheduler.kt
│   │   │   │   ├── TransactionLogger.kt
│   │   │   │   ├── Constants.kt
│   │   ├── resources/
│   │   │   ├── logback-spring.xml
│   │   │   ├── application.properties
│   ├── test/
│   │   ├── kotlin/com/tabdil_exchange/test_project/
│   │   │   ├── NegativeBalanceTests.kt
│   │   │   ├── ThroughputTests.kt
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── pom.xml
├── README.md
```

## Setup Instructions

### Clone the Repository

```bash
git clone https://github.com/your-repo/banking-system.git
cd banking-system
```

### Local Setup

1. **Install Java 21**:

   - Download and install Eclipse Temurin 21.
   - Verify: `java -version`

2. **Install Maven**:

   - Download and install Maven.
   - Verify: `mvn -version`

3. **Configure Database**:

   - Install PostgreSQL 15 locally or use Docker (see Docker Setup).
   - Create a database named `banking` with user `soroush` and password `Soroush1381`.

4. **Update** `application.properties`:

   - Edit `src/main/resources/application.properties`:

     ```properties
     spring.datasource.url=jdbc:postgresql://localhost:5432/banking
     spring.datasource.username=soroush
     spring.datasource.password=Soroush1381
     spring.jpa.hibernate.ddl-auto=update
     spring.jpa.show-sql=true
     balance.check.interval=60000
     logging.level.org.springframework=INFO
     logging.level.org.hibernate.SQL=DEBUG
     ```

5. **Build and Run**:

   ```bash
   mvn clean package -DskipTests
   java -jar target/test_project-0.0.1-SNAPSHOT.jar
   ```

   - The application runs on `http://localhost:8080`.

### Docker Setup

1. **Ensure Docker is Installed**:

   - Verify: `docker --version` and `docker-compose --version`

2. **Build and Run**:

   ```bash
   docker-compose up --build
   ```

   - Builds the application using `Dockerfile` and starts PostgreSQL and the app.
   - Access the app at `http://localhost:8080`.
   - View logs in the terminal or with `docker-compose logs app`.

3. **Docker Compose Configuration**:

   - `docker-compose.yml` defines:
     - `db`: PostgreSQL 15 with database `banking`, user `soroush`, password `Soroush1381`.
     - `app`: Spring Boot app built from `Dockerfile`, exposed on port 8080.
     - Healthchecks ensure the database is ready before the app starts.
     - TTY and logging settings ensure console log visibility.

4. **Dockerfile**:

   - Multi-stage build:
     - **Build Stage**: Uses `maven:3.9.6-eclipse-temurin-21` to compile the app.
     - **Run Stage**: Uses `eclipse-temurin:21-jre-alpine` for a lightweight runtime.
   - Includes `logback-spring.xml` in the JAR (`BOOT-INF/classes/`).
   - Sets `-Xmx512m` and Logback’s JUL manager for memory and logging optimization.

## API Endpoints

- **Deposit**:

  ```bash
  curl -X POST http://localhost:8080/api/transactions/deposit \
    -H "Content-Type: application/json" \
    -d '{"account_id":"1001","amount":"500.00","transaction_id":"123456789"}'
  ```

  - Response: `{ "transaction_id": "123456789", "account_id": "1001", "new_balance": "500.00", "status": "completed" }`

- **Withdraw**:

  ```bash
  curl -X POST http://localhost:8080/api/transactions/withdraw \
    -H "Content-Type: application/json" \
    -d '{"account_id":"1001","amount":"100.00","transaction_id":"987654321"}'
  ```

  - Response: `{ "transaction_id": "987654321", "account_id": "1001", "current_balance": "400.00", "requested_amount": "100.00", "status": "completed" }`

## Logging

- **Configuration**: `src/main/resources/logback-spring.xml` directs logs to the console.

- **Log Format**: Structured JSON for `TRANSACTION_MONITOR` logger, including:

  - `event`: `OPERATION`, `TPS_REPORT`, `COUNTER_RESET`, `TRANSACTION_STATS`.
  - `timestamp`: Operation time.
  - `operation`: `DEPOSIT`, `WITHDRAWAL`, `BALANCE_CHECK`, etc.
  - `status`: `START`, `SUCCESS`, `FAILURE`.
  - `parameters`: Operation details (e.g., `transactionId`, `accountId`, `amount`).
  - `reason`: Failure reason (e.g., `Insufficient funds`, `OptimisticLockingFailure`).
  - `durationMs`: Operation duration.

- **Example Log** (successful deposit):

  ```json
  {
    "event": "OPERATION",
    "timestamp": "2025-05-09 10:00:00.123",
    "operation": "DEPOSIT",
    "status": "SUCCESS",
    "parameters": {
      "transactionId": "123456789",
      "accountId": "1001",
      "amount": "500.00",
      "newBalance": "500.00"
    },
    "durationMs": 50
  }
  ```

- **Viewing Logs**:

  - Local: Logs appear in the console when running `java -jar`.
  - Docker: Use `docker-compose up` or `docker logs banking-system`.

## Testing

The project includes two test suites in `src/test/kotlin/com/tabdil_exchange/test_project/` to validate functionality and performance.

### Prerequisites

- Docker (for Testcontainers).
- Maven (`mvn test`).


### Running All Tests

```bash
mvn clean test
```

### Testing in Docker

1. Start the containers:

   ```bash
   docker-compose up --build -d
   ```

2. Run tests inside the `app` container:

   ```bash
   docker exec -it banking-system mvn test
   ```

3. View test logs:

   ```bash
   docker-compose logs app
   ```

## Troubleshooting

- **Build Fails**:
  - Ensure `pom.xml` includes dependencies for Spring Boot, JPA, PostgreSQL, Testcontainers, Jackson, and Kotlin coroutines.
  - Run `mvn clean package` locally to debug.
- **Database Connection Issues**:
  - Verify `spring.datasource.*` in `application.properties` or `docker-compose.yml` matches PostgreSQL settings (`banking`, `soroush`, `Soroush1381`).
  - Check `docker ps` to ensure the `db` container is healthy.
- **Logs Not Visible**:
  - Confirm `logback-spring.xml` is in `src/main/resources/`.
  - Ensure `docker-compose.yml` has `tty: true` and `logging` settings.
  - Run `docker-compose logs app` to inspect.
- **Tests Fail**:
  - **Negative Balance Tests**: If the balance goes negative, verify `TransactionService` uses `findByIdLocked` with `PESSIMISTIC_WRITE`.
  - **Throughput Tests**: If TPS is low, adjust `numThreads` or check database performance (e.g., add indexes).
  - Ensure Docker is running for Testcontainers.
- **JAR Not Found**:
  - Verify `pom.xml` has `<artifactId>test_project</artifactId>`.
  - Check `target/` for `test_project-0.0.1-SNAPSHOT.jar` after `mvn package`.


## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Commit changes: `git commit -m "Add your feature"`.
4. Push to the branch: `git push origin feature/your-feature`.
5. Open a pull request with a detailed description.



Built with ❤️ by Soroush Eskandarie