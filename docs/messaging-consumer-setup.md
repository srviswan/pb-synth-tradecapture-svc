# Trade Message Consumer Setup

## Overview

The PB Synthetic Trade Capture Service supports real-time trade processing from message queues. The service abstracts the incoming message source through configuration, allowing you to switch between different messaging systems:

- **Kafka**: Used for local development
- **Solace PubSub+**: Used for production

## Configuration

### Kafka (Local Development)

To enable Kafka consumer for local development, set the following in `application.yml` or environment variables:

```yaml
messaging:
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
    topics:
      input: trade-capture-input
    consumer:
      group-id: pb-synth-tradecapture-svc
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 10
  solace:
    enabled: false
```

### Solace (Production)

To enable Solace consumer for production, set the following:

```yaml
messaging:
  solace:
    enabled: true
    host: solace-host.example.com
    port: 55555
    vpn: production-vpn
    username: service-account
    password: ${SOLACE_PASSWORD}
    queues:
      input: trade/capture/input
      dlq: trade/capture/dlq
  kafka:
    enabled: false
```

## Docker Compose Setup

The `docker-compose.yml` includes Kafka for local development:

- **Zookeeper**: Required for Kafka coordination
- **Kafka**: Message broker for local development

The service is configured to use Kafka by default when running in Docker:

```yaml
environment:
  - KAFKA_ENABLED=true
  - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
  - SOLACE_ENABLED=false
```

## Message Format

Incoming messages must be in protobuf format (`TradeCaptureMessage`). The consumer:

1. Deserializes the protobuf message
2. Converts it to `TradeCaptureRequest`
3. Processes it through `TradeCaptureService`
4. Publishes the result to output queues/subscribers

## Architecture

```
┌─────────────────┐
│  Message Queue  │
│  (Kafka/Solace) │
└────────┬────────┘
         │
         │ Protobuf Message
         ▼
┌─────────────────────────┐
│ TradeMessageConsumer    │
│ - KafkaTradeMessage... │
│ - SolaceTradeMessage...│
└────────┬────────────────┘
         │
         │ TradeCaptureRequest
         ▼
┌─────────────────────────┐
│ TradeMessageProcessor   │
└────────┬────────────────┘
         │
         │ Process Trade
         ▼
┌─────────────────────────┐
│ TradeCaptureService     │
│ - Enrichment            │
│ - Rules                 │
│ - Validation            │
│ - State Management      │
└────────┬────────────────┘
         │
         │ SwapBlotter
         ▼
┌─────────────────────────┐
│ SwapBlotterPublisher    │
│ - Kafka/Solace/RabbitMQ │
│ - Webhooks              │
│ - REST APIs             │
└─────────────────────────┘
```

## Consumer Lifecycle

The consumer is automatically started when the application starts via `TradeMessageConsumerConfig`. Only one consumer is active at a time based on configuration:

- If `messaging.kafka.enabled=true`, `KafkaTradeMessageConsumer` is active
- If `messaging.solace.enabled=true`, `SolaceTradeMessageConsumer` is active

## Error Handling

### Kafka Consumer

- Manual acknowledgment: Messages are acknowledged only after successful processing
- Error handling: Failed messages are logged and can be retried via Kafka's retry mechanism
- DLQ: Failed messages can be sent to a dead-letter topic (to be configured)

### Solace Consumer

- Acknowledgment: Messages are acknowledged after successful processing
- DLQ: Failed messages are sent to the configured DLQ (`trade/capture/dlq`)
- Retry: Retry logic can be configured via Solace queue properties

## Testing

### Local Testing with Kafka

1. Start the services:
   ```bash
   docker-compose up -d
   ```

2. Publish a test message to Kafka:
   ```bash
   docker exec -it pb-synth-tradecapture-kafka kafka-console-producer \
     --broker-list localhost:9092 \
     --topic trade-capture-input
   ```

3. Send a protobuf message (base64 encoded or binary)

4. Check the service logs:
   ```bash
   docker-compose logs -f trade-capture-service
   ```

### Production Testing with Solace

1. Configure Solace connection details
2. Publish a test message to the input queue
3. Monitor the service logs for processing

## Monitoring

- Consumer status: Check application logs for consumer start/stop messages
- Processing metrics: Monitor trade processing through application metrics
- Queue metrics: Monitor queue depth and consumer lag (Kafka) or message count (Solace)

## Troubleshooting

### Consumer Not Starting

- Check that exactly one messaging system is enabled (`kafka.enabled` or `solace.enabled`)
- Verify connection details (host, port, credentials)
- Check application logs for startup errors

### Messages Not Processing

- Verify the topic/queue name matches configuration
- Check message format (must be protobuf `TradeCaptureMessage`)
- Verify consumer group (Kafka) or queue subscription (Solace)
- Check application logs for processing errors

### High Consumer Lag (Kafka)

- Increase consumer instances (horizontal scaling)
- Optimize processing logic
- Check for processing bottlenecks

