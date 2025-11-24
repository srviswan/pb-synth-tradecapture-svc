# PB Synthetic Trade Capture Service

Service for capturing and enriching trade lots with CDM compliance, applying economic and non-economic rules, and publishing enriched SwapBlotter to multiple subscribers.

## Features

- **Dual Processing Modes**: Real-time streaming via messaging queues and REST API
- **Multi-Subscriber Publishing**: SwapBlotter published to multiple downstream services via queues and/or APIs
- **Flexible Publishing**: Supports async messaging (Solace, Kafka, RabbitMQ, etc.) and API-based delivery (webhooks, REST, gRPC)
- **Partition-Aware Sequencing**: Maintains sequence per Account/Book + Security combination
- **Rule-Based Processing**: Configurable Economic, Non-Economic, and Workflow rules
- **CDM-Compliant State Management**: Follows CDM PositionStatusEnum state transitions
- **Idempotency**: Prevents duplicate trade processing with multiple layers of deduplication
- **High Scalability**: Designed for 2M trades/day with burst capacity during market hours
- **Manual Trade Entry**: Supports manual trade entry screen integration

## Technology Stack

- **Java 21**
- **Spring Boot 3.2.0**
- **PostgreSQL** (for state and idempotency persistence)
- **Redis** (for caching)
- **Solace PubSub+** (primary messaging system)
- **Kafka/RabbitMQ** (alternative messaging systems, configurable)
- **Maven** (build tool)

## Building and Running

### Prerequisites

- Java 21
- Maven 3.8+
- PostgreSQL (or use Docker)
- Redis (or use Docker)

### Build

```bash
./mvnw clean install
```

### Run

```bash
./mvnw spring-boot:run
```

Or with specific profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Configuration

Configuration is in `src/main/resources/application.yml`. Key configuration areas:

- **Database**: PostgreSQL connection settings
- **Redis**: Cache connection settings
- **Messaging**: Solace, Kafka, RabbitMQ configuration
- **Services**: External service endpoints (SecurityMaster, Account, Rule Management, etc.)
- **Idempotency**: Idempotency window and storage configuration
- **Publishing**: Subscriber configuration for multi-subscriber publishing

## API Endpoints

### Trade Capture

- `POST /api/v1/trades/capture` - Synchronous trade capture
- `POST /api/v1/trades/capture/async` - Asynchronous trade capture
- `POST /api/v1/trades/capture/batch` - Batch trade capture
- `POST /api/v1/trades/manual-entry` - Manual trade entry
- `GET /api/v1/trades/capture/{tradeId}` - Get SwapBlotter by trade ID

### Rules Management

- `POST /api/v1/rules/economic` - Add/update economic rule
- `POST /api/v1/rules/non-economic` - Add/update non-economic rule
- `POST /api/v1/rules/workflow` - Add/update workflow rule
- `GET /api/v1/rules` - List all active rules
- `GET /api/v1/rules/{ruleId}` - Get specific rule
- `DELETE /api/v1/rules/{ruleId}` - Delete/disable rule

### Health

- `GET /api/v1/health` - Health check

See `docs/api/trade-capture-service-openapi.yaml` for complete API specification.

## Idempotency

The service implements multiple layers of idempotency:

1. **Trade ID Uniqueness**: Each trade must have a unique `tradeId`
2. **Database-Level Deduplication**: Idempotency records stored in database
3. **Message-Level Deduplication**: Processed message IDs tracked
4. **Partition-Level Idempotency**: Distributed locks prevent race conditions
5. **REST API Idempotency**: Supports `Idempotency-Key` HTTP header

Duplicate trades within the idempotency window (default 24 hours) return cached SwapBlotter without re-processing.

## Publishing Strategy

SwapBlotter can be published to multiple subscribers via:

- **Async Messaging Queues**: Solace PubSub+, Kafka, RabbitMQ
- **API-Based Publishing**: Webhooks (HTTP POST), REST API push, gRPC streaming

Each subscriber is configured independently and failures in one subscriber don't affect others.

## Testing

See `docs/testing-plan.md` for comprehensive testing strategy.

Run tests:

```bash
# Unit tests only
./mvnw test

# Integration tests with mocked services
./mvnw test -Ptest-mocked

# Integration tests with Testcontainers
./mvnw test -Ptest-integration

# E2E tests with real services
./mvnw test -Ptest-real
```

## Documentation

- **Service Design**: `docs/trade_capture_service_design.md`
- **Data Model Design**: `docs/data-model-design.md`
- **Testing Plan**: `docs/testing-plan.md`
- **API Specification**: `docs/api/trade-capture-service-openapi.yaml`
- **Protobuf Schema**: `docs/trade_capture_message.proto`

## Docker Support

The service is designed to run in Docker containers. See `docs/trade_capture_service_design.md` for containerization considerations and deployment strategies.

## License

[Add license information]

