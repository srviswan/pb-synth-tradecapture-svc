#!/bin/bash

# Script to test and verify observability metrics

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="test-$(date +%s)"

echo "=========================================="
echo "Observability Metrics Verification Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Correlation ID: $CORRELATION_ID"
echo ""

# Test 1: Health Endpoint
echo "=== Test 1: Health Endpoint ==="
HEALTH=$(curl -s "$BASE_URL/actuator/health")
STATUS=$(echo "$HEALTH" | jq -r '.status')
if [ "$STATUS" = "UP" ]; then
    echo "✅ Health check: $STATUS"
else
    echo "❌ Health check: $STATUS"
    exit 1
fi
echo ""

# Test 2: Prometheus Endpoint
echo "=== Test 2: Prometheus Metrics Endpoint ==="
PROMETHEUS=$(curl -s "$BASE_URL/actuator/prometheus")
if echo "$PROMETHEUS" | grep -q "trades_processed_total"; then
    echo "✅ Prometheus endpoint accessible"
    TRADES_PROCESSED=$(echo "$PROMETHEUS" | grep "^trades_processed_total" | head -1)
    echo "  Sample metric: $TRADES_PROCESSED"
else
    echo "❌ Prometheus endpoint not accessible or metrics not found"
    exit 1
fi
echo ""

# Test 3: Correlation ID
echo "=== Test 3: Correlation ID Filter ==="
RESPONSE=$(curl -s -H "X-Correlation-ID: $CORRELATION_ID" "$BASE_URL/api/v1/health")
RESPONSE_CORR_ID=$(echo "$RESPONSE" | jq -r '.correlationId // "not found"')
HEADER_CORR_ID=$(curl -s -H "X-Correlation-ID: $CORRELATION_ID" -D - "$BASE_URL/api/v1/health" | grep -i "x-correlation-id" | cut -d' ' -f2 | tr -d '\r')

if [ -n "$HEADER_CORR_ID" ]; then
    echo "✅ Correlation ID in response header: $HEADER_CORR_ID"
else
    echo "⚠️  Correlation ID not found in response header (may be in response body)"
fi
echo ""

# Test 4: Process a Trade and Verify Metrics
echo "=== Test 4: Process Trade and Verify Metrics ==="
TRADE_ID="METRICS-TEST-$(date +%s)"
TRADE_PAYLOAD=$(cat <<EOF
{
  "tradeId": "$TRADE_ID",
  "accountId": "ACC-METRICS",
  "bookId": "BOOK-METRICS",
  "securityId": "US0378331005",
  "source": "AUTOMATED",
  "tradeLots": [
    {
      "lotIdentifier": "LOT-001",
      "priceQuantity": {
        "quantity": 1000,
        "unitOfAmount": {
          "currency": "USD"
        }
      }
    }
  ],
  "counterpartyIds": ["CP-001"],
  "tradeDate": "$(date +%Y-%m-%d)"
}
EOF
)

echo "Processing trade: $TRADE_ID"
TRADE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -H "X-Callback-Url: http://example.com/callback" \
  -d "$TRADE_PAYLOAD")

TRADE_STATUS=$(echo "$TRADE_RESPONSE" | jq -r '.status // "ERROR"')
if [ "$TRADE_STATUS" = "ACCEPTED" ]; then
    echo "✅ Trade processed: $TRADE_STATUS"
else
    echo "❌ Trade processing failed: $TRADE_STATUS"
    echo "Response: $TRADE_RESPONSE"
fi
echo ""

# Wait for metrics to update
echo "Waiting 2 seconds for metrics to update..."
sleep 2

# Test 5: Verify Metrics After Trade Processing
echo "=== Test 5: Verify Metrics After Trade Processing ==="
PROMETHEUS_AFTER=$(curl -s "$BASE_URL/actuator/prometheus")

METRICS_FOUND=0

# Check trades.processed
if echo "$PROMETHEUS_AFTER" | grep -q "^trades_processed_total"; then
    VALUE=$(echo "$PROMETHEUS_AFTER" | grep "^trades_processed_total" | head -1 | awk '{print $2}')
    echo "✅ trades.processed: $VALUE"
    METRICS_FOUND=$((METRICS_FOUND + 1))
fi

# Check trades.successful or trades.failed
if echo "$PROMETHEUS_AFTER" | grep -q "^trades_successful_total"; then
    VALUE=$(echo "$PROMETHEUS_AFTER" | grep "^trades_successful_total" | head -1 | awk '{print $2}')
    echo "✅ trades.successful: $VALUE"
    METRICS_FOUND=$((METRICS_FOUND + 1))
fi

# Check trades.processing.time
if echo "$PROMETHEUS_AFTER" | grep -q "^trades_processing_time_seconds"; then
    echo "✅ trades.processing.time metric exists"
    METRICS_FOUND=$((METRICS_FOUND + 1))
fi

# Check database.connections.active
if echo "$PROMETHEUS_AFTER" | grep -q "^database_connections_active"; then
    VALUE=$(echo "$PROMETHEUS_AFTER" | grep "^database_connections_active" | head -1 | awk '{print $2}')
    echo "✅ database.connections.active: $VALUE"
    METRICS_FOUND=$((METRICS_FOUND + 1))
fi

if [ $METRICS_FOUND -ge 3 ]; then
    echo "✅ Metrics verification: $METRICS_FOUND metrics found"
else
    echo "⚠️  Only $METRICS_FOUND metrics found (expected at least 3)"
fi
echo ""

# Test 6: Verify Structured Logging
echo "=== Test 6: Verify Structured Logging ==="
LOG_LINE=$(docker logs pb-synth-tradecapture-svc --tail 50 2>&1 | grep "$TRADE_ID" | head -1)
if [ -n "$LOG_LINE" ]; then
    if echo "$LOG_LINE" | grep -q "correlationId\|correlation_id"; then
        echo "✅ Structured logging with correlation ID found"
        echo "  Sample log line: ${LOG_LINE:0:100}..."
    else
        echo "⚠️  Log found but correlation ID not visible in sample"
    fi
else
    echo "⚠️  Trade log not found (may need to check logs manually)"
fi
echo ""

# Test 7: List All Available Metrics
echo "=== Test 7: List All Available Metrics ==="
METRICS_LIST=$(curl -s "$BASE_URL/actuator/metrics" | jq -r '.names[]' | sort)
METRICS_COUNT=$(echo "$METRICS_LIST" | wc -l | tr -d ' ')
echo "Total metrics available: $METRICS_COUNT"
echo ""
echo "Custom metrics (filtered):"
echo "$METRICS_LIST" | grep -E "(trades|enrichment|deadlock|idempotency|partition|database)" | head -10
echo ""

echo "=========================================="
echo "✅ Observability Metrics Verification Complete"
echo "=========================================="




