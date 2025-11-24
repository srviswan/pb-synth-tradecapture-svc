# Testing Guide for pb-synth-tradecapture-svc

This directory contains comprehensive test suites for the PB Synthetic Trade Capture Service.

## Test Structure

### Unit Tests (No Integration)
- **Service Layer**: `service/` - Tests for business logic with mocked dependencies
- **Controllers**: `controller/` - REST API tests using MockMvc
- **Models**: `model/` - Data model serialization/deserialization tests
- **Protobuf**: `proto/` - Protobuf message serialization tests

### Integration Tests (With Mocked Dependencies)
- **External Services**: `integration/external/` - WireMock-based tests for external services
- **Repositories**: `integration/repository/` - Database integration tests
- **Messaging**: `integration/messaging/` - Solace message queue tests
- **Cache**: `integration/cache/` - Redis cache tests

### End-to-End Tests
- **E2E Tests**: `e2e/` - Complete flow tests with real services
- **Performance Tests**: `performance/` - Load and capacity tests

## Running Tests

### Unit Tests Only
```bash
./mvnw test
```

### Integration Tests with Mocked Services
```bash
./mvnw test -Ptest-mocked
```

### Integration Tests with Testcontainers
```bash
./mvnw test -Ptest-integration
```

### E2E Tests with Real Services
```bash
./mvnw test -Ptest-real
```

## Test Configuration

Test configurations are located in `src/test/resources/`:
- `application-test.yml` - Base test configuration
- `application-test-mocked.yml` - All services mocked
- `application-test-integration.yml` - Testcontainers configuration
- `application-test-real.yml` - Real test environment services

## Test Utilities

Test utilities and builders are in `testutil/`:
- `TradeCaptureRequestBuilder` - Build test trade requests
- `SwapBlotterBuilder` - Build test swap blotters
- `TestFixtures` - Sample test data
- `WireMockHelper` - WireMock setup helper

## Service Dependencies

The service depends on:
1. **Solace PubSub+** - Message queue
2. **SecurityMasterService** - Reference data service
3. **AccountService** - Reference data service
4. **Rule Management Service** - External rule service
5. **Approval Workflow Service** - Workflow service
6. **Redis** - Cache layer
7. **Database** - State repository

All dependencies can be switched between mocked and real implementations via Spring profiles.

