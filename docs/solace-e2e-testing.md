# Solace End-to-End Testing Guide

## Overview

This guide explains how to run end-to-end tests for the Trade Capture Service with Solace PubSub+ messaging enabled.

## Prerequisites

1. **Docker and Docker Compose** installed and running
2. **Solace PubSub+** container configured in `docker-compose.yml`
3. **Service configured** with `SOLACE_ENABLED=true`
4. **jq** installed (for JSON parsing in test script)

## Quick Start

### 1. Start Services with Solace Enabled

```bash
# Start Solace container
docker-compose up -d solace

# Wait for Solace to be ready (~2 minutes)
docker-compose ps solace

# Start service with Solace enabled
SOLACE_ENABLED=true docker-compose up -d trade-capture-service

# Verify service is healthy
curl http://localhost:8080/api/v1/health
```

### 2. Run E2E Tests

```bash
# Make script executable (if not already)
chmod +x scripts/e2e-test-solace.sh

# Run the tests
./scripts/e2e-test-solace.sh
```

## Test Coverage

The Solace E2E test script (`scripts/e2e-test-solace.sh`) verifies:

1. **Service Health Check** - Service is running and healthy
2. **Solace Configuration** - Solace components are initialized
3. **API → Solace Publishing** - API calls publish to Solace input topic
4. **Message Processing** - Messages are consumed and processed via Solace
5. **Router Verification** - SolaceMessageRouter is active and routing messages
6. **Consumer Verification** - SolaceTradeMessageConsumer is active
7. **Partition Locking** - Concurrent trades on same partition are handled correctly
8. **Output Publishing** - SwapBlotter is published to Solace output topics
9. **End-to-End Flow** - Complete flow from API to database via Solace

## Architecture Flow

```
API Call (POST /api/v1/trades/capture)
    │
    ▼
TradePublishingService
    │
    ▼
SolaceTradeInputPublisher
    │
    ▼
Solace Topic: trade/capture/input
    │
    ▼
SolaceMessageRouter
    │
    ▼
Partition-Specific Topics: trade/capture/input/{partitionKey}
    │
    ▼
SolaceTradeMessageConsumer
    │
    ▼
TradeCaptureService (Processing)
    │
    ▼
SolaceSwapBlotterPublisher
    │
    ▼
Output Topics: trade/capture/blotter/{partitionKey}
```

## Configuration

### Environment Variables

The test script uses the following environment variables (with defaults):

```bash
BASE_URL=http://localhost:8080/api/v1
SOLACE_HOST=localhost
SOLACE_PORT=55555
SOLACE_ADMIN_PORT=8080
SOLACE_VPN=default
SOLACE_USERNAME=admin
SOLACE_PASSWORD=admin
```

### Service Configuration

Ensure the service is configured with:

```yaml
messaging:
  solace:
    enabled: true
    host: solace  # or localhost if not in Docker
    port: 55555
    vpn: default
    username: admin
    password: admin
    router:
      enabled: true
    consumer:
      topic-pattern: trade/capture/input/>
```

## Test Results

Test results are saved to `./e2e-results-solace/`:

- `solace-trade-response.json` - API response for trade capture
- `job-status.json` - Job status response
- `partition-trade1-solace.json` - First partition trade response
- `partition-trade2-solace.json` - Second partition trade response
- `last_trade_id.txt` - Last trade ID for verification
- `last_job_id.txt` - Last job ID for verification

## Troubleshooting

### Solace Container Not Starting

```bash
# Check if port 55555 is already in use
lsof -i :55555

# Check Solace container logs
docker-compose logs solace

# Restart Solace container
docker-compose restart solace
```

### Service Not Using Solace

```bash
# Check service logs for Solace initialization
docker-compose logs trade-capture-service | grep -i solace

# Verify environment variable
docker-compose exec trade-capture-service env | grep SOLACE_ENABLED

# Restart service with Solace enabled
SOLACE_ENABLED=true docker-compose restart trade-capture-service
```

### Messages Not Processing

1. **Check Router Logs**:
   ```bash
   docker-compose logs trade-capture-service | grep -i router
   ```

2. **Check Consumer Logs**:
   ```bash
   docker-compose logs trade-capture-service | grep -i "solace.*consumer"
   ```

3. **Verify Topics/Queues**:
   - Input topic: `trade/capture/input`
   - Partition topics: `trade/capture/input/{partitionKey}`
   - Output topics: `trade/capture/blotter/{partitionKey}`

4. **Check Solace Management API**:
   ```bash
   curl http://localhost:8080/SEMP
   ```

### Test Failures

1. **Service Not Ready**: Wait longer for service startup (increase `SERVICE_STARTUP_WAIT`)
2. **Solace Not Ready**: Wait longer for Solace startup (increase `SOLACE_STARTUP_WAIT`)
3. **Processing Timeout**: Increase `MESSAGE_PROCESSING_WAIT` to allow more time for async processing

## Manual Testing

### Publish Message Directly to Solace

If you want to test Solace directly without the API:

```bash
# Using Solace CLI (if available)
solace-pubsub-cli publish \
  --host localhost \
  --port 55555 \
  --vpn default \
  --username admin \
  --password admin \
  --topic "trade/capture/input" \
  --message "$(cat test-message.json)"
```

### Verify Messages in Solace

```bash
# Check Solace management API
curl http://localhost:8080/SEMP/v2/monitor/msgVpns/default/queues

# Check service logs for consumed messages
docker-compose logs trade-capture-service | grep -i "consumed\|processed"
```

## Next Steps

1. **Complete Solace Integration**: Replace boilerplate code with actual Solace JMS API calls
2. **Add Integration Tests**: Create Java-based integration tests using Testcontainers
3. **Performance Testing**: Test throughput and latency with Solace
4. **Monitoring**: Add metrics for Solace message routing and consumption

## Related Documentation

- [Solace Partition Routing Implementation](./solace-partition-routing-test-results.md)
- [Messaging Consumer Setup](./messaging-consumer-setup.md)
- [Architecture Improvements Roadmap](./architecture-improvements-roadmap.md)

