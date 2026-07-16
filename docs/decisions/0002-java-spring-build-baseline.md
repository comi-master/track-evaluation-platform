# ADR 0002: Java, Spring Boot, build, and container baseline

- Status: Accepted for milestone 0
- Date: 2026-07-15

## Context

The project initially targeted Java 21, but the project owner subsequently standardized the supported runtime and compiler baseline on Java 17. Spring Boot 3.5.15 requires Java 17 or later and remains compatible with this decision. Maven, formatting, coverage reporting, health endpoints, and reproducible local dependencies remain required.

## Decision

Compile and run against Java 17 and inherit Spring Boot 3.5.15 dependency management. Both `java.version` and `maven.compiler.release` are 17; the parent-managed Maven Compiler Plugin therefore compiles with release 17. Pin only tooling not managed by Boot: Spotless 2.46.1 with google-java-format 1.28.0 and JaCoCo 0.8.13. Use official/publisher images pinned to MySQL 8.4.10, Redis 8.2.7 Alpine, RabbitMQ 4.2.8 management, and MinIO release `2025-09-07T16-13-09Z`.

Maven Wrapper 3.3.4 targets Maven 3.9.11 so a global Maven install is unnecessary. Application dependencies remain minimal until their milestones.

The final milestone 0 build used JetBrains OpenJDK 17.0.14 from `D:\java\JDK17` for both `java` and Maven. It compiled 22 production sources and 6 test sources with release 17, ran 19 tests with no failures, errors, or skips, and produced class-file major version 61. Spotless, clean verification, dependency inspection, and the packaged application smoke test all passed on that runtime.

Docker Desktop 4.82.0 with Engine 29.6.1 on Linux/amd64, Compose v5.3.0, and WSL 2.7.10.0 with Ubuntu on WSL 2 were verified. After two transient Docker Hub `EOF` failures, a finite third pull succeeded. All four services reached healthy and passed service-specific checks. Published middleware ports default to `127.0.0.1`. MySQL and Redis health checks authenticate through environment variables rather than password arguments. Redis accepts only a documented 16-128-character safe alphabet, validates before startup without echoing the value, and generates a `600 redis:redis` temporary configuration on every start; the real value is absent from container command/PID 1 arguments, health-check metadata, and logs.

An earlier build used Oracle JDK 25.0.3 with compiler release 21 and passed seven tests. This is retained strictly as a historical record; it is not the current baseline or the evidence used for milestone acceptance.

## Consequences

Pinning improves repeatability but requires deliberate upgrades and vulnerability review. Java 17 is now the sole supported project baseline; JDK 21 and JDK 25 must not be used as substitutes for acceptance. Compose credentials remain local and ignored, named volumes persist across normal restarts, and middleware business integration is deferred to later milestones.
