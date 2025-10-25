# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a TDD practice project for building a user point management system using Spring Boot and Kotlin. The project focuses on implementing point charge/use functionality with transaction history tracking.

## Build & Test Commands

**Build the project:**
```bash
./gradlew build
```

**Run all tests:**
```bash
./gradlew test
```

**Run specific test:**
```bash
./gradlew test --tests "ClassName.testMethodName"
```

**Run the application:**
```bash
./gradlew bootRun
```

**Clean build:**
```bash
./gradlew clean build
```

## Architecture

### Package Structure

- `io.hhplus.tdd.point` - Point domain models and controller
  - `PointController.kt` - REST endpoints for point operations
  - `UserPoint.kt` - User point data class
  - `PointHistory.kt` - Point transaction history data class
  - `TransactionType` - Enum for CHARGE/USE transaction types

- `io.hhplus.tdd.database` - In-memory database tables (DO NOT MODIFY)
  - `UserPointTable.kt` - User point storage with random delays (0-200ms for reads, 0-300ms for writes)
  - `PointHistoryTable.kt` - Point transaction history storage with random delays (0-300ms)

- `io.hhplus.tdd` - Application root
  - `TddApplication.kt` - Spring Boot application entry point
  - `ApiControllerAdvice.kt` - Global exception handler

### Key Design Constraints

**DO NOT MODIFY TABLE CLASSES**: The `UserPointTable` and `PointHistoryTable` classes simulate database behavior with artificial delays and should only be used through their public APIs. These classes intentionally include random delays to simulate real database latency.

**Data Storage**: Both table classes use in-memory collections (HashMap for UserPointTable, MutableList for PointHistoryTable) to simulate database tables without actual persistence.

**Transaction Types**: Point operations are categorized as either CHARGE (충전) or USE (사용) via the `TransactionType` enum.

### Implementation Requirements

The PointController contains four TODO endpoints that need TDD implementation:

1. `GET /point/{id}` - Retrieve user point balance
2. `GET /point/{id}/histories` - Retrieve user point transaction history
3. `PATCH /point/{id}/charge` - Charge points to user account
4. `PATCH /point/{id}/use` - Use points from user account

When implementing these endpoints, create service layer classes to handle business logic. Tests should be written first following TDD methodology.

## Technology Stack

- **Language**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Gradle with Kotlin DSL
- **JDK**: Java 17
- **Testing**: JUnit 5 with Kotlin Test

## Kotlin-Specific Configuration

The project uses Kotlin compiler plugins for Spring and JPA:
- `allOpen` plugin for JPA entities (@Entity, @MappedSuperclass, @Embeddable)
- `noArg` plugin for JPA entities
- JSR-305 strict null-safety checks enabled
