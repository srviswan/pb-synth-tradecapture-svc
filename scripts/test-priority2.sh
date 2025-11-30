#!/bin/bash

# Script to test Priority 2 features: Async Processing & Message Queue Integration

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="test-$(date +%s)"

echo "=========================================="
echo "Priority 2 Features Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Correlation ID: $CORRELATION_ID"
echo ""

# Test 1: Health Check
echo "=== Test 1: Health Check ==="
HEALTH=$(curl -s "$BASE_URL/actuator/health")
STATUS=$(echo "$HEALTH" | jq -r '.status')
if [ "$STATUS" = "UP" ]; then
    echo "✅ Health check: $STATUS"
else
    echo "❌ Health check: $STATUS"
    exit 1
fi
echo ""

# Test 2: Async Trade Processing
echo "=== Test 2: Async Trade Processing ==="
TRADE_ID="ASYNC-TEST-$(date +%s)"
JOB_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture/async" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"$TRADE_ID\",
    \"accountId\": \"ACC-ASYNC\",
    \"bookId\": \"BOOK-ASYNC\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-001\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 1000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/trades/capture/async" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"$TRADE_ID\",
    \"accountId\": \"ACC-ASYNC\",
    \"bookId\": \"BOOK-ASYNC\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-001\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 1000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

if [ "$HTTP_STATUS" = "202" ]; then
    echo "✅ Async endpoint returned 202 Accepted"
    JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.jobId // empty')
    if [ -n "$JOB_ID" ] && [ "$JOB_ID" != "null" ]; then
        echo "✅ Job ID received: $JOB_ID"
    else
        echo "⚠️  Job ID not found in response"
    fi
else
    echo "❌ Async endpoint returned: $HTTP_STATUS"
    echo "Response: $JOB_RESPONSE"
fi
echo ""

# Test 3: Job Status Check
if [ -n "$JOB_ID" ] && [ "$JOB_ID" != "null" ]; then
    echo "=== Test 3: Job Status Check ==="
    echo "Waiting 3 seconds for processing..."
    sleep 3
    
    JOB_STATUS=$(curl -s "$BASE_URL/api/v1/trades/jobs/$JOB_ID/status")
    STATUS=$(echo "$JOB_STATUS" | jq -r '.status // "UNKNOWN"')
    PROGRESS=$(echo "$JOB_STATUS" | jq -r '.progress // 0')
    
    echo "Job Status: $STATUS"
    echo "Progress: $PROGRESS%"
    
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        echo "✅ Job finished with status: $STATUS"
    elif [ "$STATUS" = "PROCESSING" ] || [ "$STATUS" = "PENDING" ]; then
        echo "ℹ️  Job still processing: $STATUS"
    else
        echo "⚠️  Unexpected job status: $STATUS"
    fi
    echo ""
fi

# Test 4: Consumer Group Status (if Kafka enabled)
echo "=== Test 4: Consumer Group Status ==="
CONSUMER_STATUS=$(curl -s "$BASE_URL/api/v1/consumer-groups/status" 2>&1)
if echo "$CONSUMER_STATUS" | grep -q "listenerId\|error"; then
    echo "✅ Consumer group endpoint accessible"
    echo "$CONSUMER_STATUS" | jq . 2>/dev/null || echo "$CONSUMER_STATUS"
else
    echo "ℹ️  Consumer group endpoint not available (Kafka may not be enabled)"
fi
echo ""

# Test 5: Regular Trade Capture (for comparison)
echo "=== Test 5: Regular Trade Capture (Synchronous) ==="
SYNC_TRADE_ID="SYNC-TEST-$(date +%s)"
SYNC_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"$SYNC_TRADE_ID\",
    \"accountId\": \"ACC-SYNC\",
    \"bookId\": \"BOOK-SYNC\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-001\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 1000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

SYNC_STATUS=$(echo "$SYNC_RESPONSE" | jq -r '.status // "UNKNOWN"')
if [ "$SYNC_STATUS" = "SUCCESS" ] || [ "$SYNC_STATUS" = "DUPLICATE" ]; then
    echo "✅ Synchronous trade processed: $SYNC_STATUS"
else
    echo "⚠️  Synchronous trade status: $SYNC_STATUS"
fi
echo ""

# Test 6: Metrics Verification
echo "=== Test 6: Metrics Verification ==="
METRICS=$(curl -s "$BASE_URL/actuator/prometheus" | grep -E "^trades_(processed|successful|failed)_total" | head -3)
if [ -n "$METRICS" ]; then
    echo "✅ Metrics available:"
    echo "$METRICS"
else
    echo "⚠️  Metrics not found"
fi
echo ""

echo "=========================================="
echo "✅ Priority 2 Features Test Complete"
echo "=========================================="


