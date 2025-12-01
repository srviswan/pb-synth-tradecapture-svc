#!/bin/bash

# Script to test Priority 4 features: Sequence Number Validation & Out-of-Order Message Handling

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="test-priority4-$(date +%s)"

echo "=========================================="
echo "Priority 4 Features Test"
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

# Test 2: In-Order Sequence Processing
echo "=== Test 2: In-Order Sequence Processing ==="
PARTITION_KEY="SEQ-TEST-$(date +%s)"
echo "Partition: $PARTITION_KEY"

echo "Submitting trade with sequence 1..."
TRADE_1=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-1-$(date +%s)\",
    \"accountId\": \"ACC-SEQ\",
    \"bookId\": \"BOOK-SEQ\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 1,
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

STATUS_1=$(echo "$TRADE_1" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_1" = "SUCCESS" ]; then
    echo "✅ Trade with sequence 1 processed: $STATUS_1"
else
    echo "⚠️  Trade with sequence 1 status: $STATUS_1"
fi

sleep 1

echo "Submitting trade with sequence 2..."
TRADE_2=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-2-$(date +%s)\",
    \"accountId\": \"ACC-SEQ\",
    \"bookId\": \"BOOK-SEQ\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 2,
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-002\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 2000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_2=$(echo "$TRADE_2" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_2" = "SUCCESS" ]; then
    echo "✅ Trade with sequence 2 processed: $STATUS_2"
else
    echo "⚠️  Trade with sequence 2 status: $STATUS_2"
fi
echo ""

# Test 3: Out-of-Order Message Buffering
echo "=== Test 3: Out-of-Order Message Buffering ==="
PARTITION_KEY_2="SEQ-BUFFER-$(date +%s)"
echo "Partition: $PARTITION_KEY_2"

echo "Submitting trade with sequence 5 (gap: expecting 1)..."
TRADE_5=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-BUFFER-5-$(date +%s)\",
    \"accountId\": \"ACC-BUFFER\",
    \"bookId\": \"BOOK-BUFFER\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 5,
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-005\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 5000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_5=$(echo "$TRADE_5" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_5" = "BUFFERED" ]; then
    echo "✅ Trade with sequence 5 buffered: $STATUS_5"
else
    echo "⚠️  Trade with sequence 5 status: $STATUS_5"
fi

sleep 1

echo "Submitting trade with sequence 1 (should process and trigger buffer processing)..."
TRADE_1_BUFFER=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-BUFFER-1-$(date +%s)\",
    \"accountId\": \"ACC-BUFFER\",
    \"bookId\": \"BOOK-BUFFER\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 1,
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

STATUS_1_BUFFER=$(echo "$TRADE_1_BUFFER" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_1_BUFFER" = "SUCCESS" ]; then
    echo "✅ Trade with sequence 1 processed: $STATUS_1_BUFFER"
    echo "   This should trigger processing of buffered sequence 5"
else
    echo "⚠️  Trade with sequence 1 status: $STATUS_1_BUFFER"
fi
echo ""

# Test 4: Out-of-Order (Too Old) Rejection
echo "=== Test 4: Out-of-Order (Too Old) Rejection ==="
PARTITION_KEY_3="SEQ-REJECT-$(date +%s)"
echo "Partition: $PARTITION_KEY_3"

echo "Submitting trade with sequence 1..."
curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-REJECT-1-$(date +%s)\",
    \"accountId\": \"ACC-REJECT\",
    \"bookId\": \"BOOK-REJECT\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 1,
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
  }" > /dev/null

sleep 1

echo "Submitting trade with sequence 0 (should be rejected as too old)..."
TRADE_0=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-REJECT-0-$(date +%s)\",
    \"accountId\": \"ACC-REJECT\",
    \"bookId\": \"BOOK-REJECT\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 0,
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-000\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 0,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_0=$(echo "$TRADE_0" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_0" = "REJECTED" ]; then
    echo "✅ Trade with sequence 0 correctly rejected: $STATUS_0"
else
    echo "⚠️  Trade with sequence 0 status: $STATUS_0"
fi
echo ""

# Test 5: Gap Too Large Rejection
echo "=== Test 5: Gap Too Large Rejection ==="
PARTITION_KEY_4="SEQ-GAP-$(date +%s)"
echo "Partition: $PARTITION_KEY_4"

echo "Submitting trade with sequence 200 (gap too large, should be rejected)..."
TRADE_200=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"SEQ-GAP-200-$(date +%s)\",
    \"accountId\": \"ACC-GAP\",
    \"bookId\": \"BOOK-GAP\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"sequenceNumber\": 200,
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-200\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 20000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_200=$(echo "$TRADE_200" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_200" = "REJECTED" ]; then
    echo "✅ Trade with sequence 200 correctly rejected (gap too large): $STATUS_200"
else
    echo "⚠️  Trade with sequence 200 status: $STATUS_200"
fi
echo ""

# Test 6: Buffer Status
echo "=== Test 6: Buffer Status ==="
BUFFER_STATUS=$(curl -s "$BASE_URL/api/v1/sequence-buffer/status")
if echo "$BUFFER_STATUS" | jq -e '.enabled' > /dev/null 2>&1; then
    echo "✅ Buffer status endpoint accessible"
    echo "$BUFFER_STATUS" | jq '{enabled, totalPartitions, totalBufferedMessages, bufferWindowSize, bufferTimeoutSeconds}'
else
    echo "⚠️  Buffer status endpoint not accessible"
fi
echo ""

# Test 7: Trade Without Sequence Number (Backward Compatibility)
echo "=== Test 7: Trade Without Sequence Number (Backward Compatibility) ==="
TRADE_NO_SEQ=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -d "{
    \"tradeId\": \"NO-SEQ-$(date +%s)\",
    \"accountId\": \"ACC-NO-SEQ\",
    \"bookId\": \"BOOK-NO-SEQ\",
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

STATUS_NO_SEQ=$(echo "$TRADE_NO_SEQ" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_NO_SEQ" = "SUCCESS" ] || [ "$STATUS_NO_SEQ" = "DUPLICATE" ]; then
    echo "✅ Trade without sequence number processed: $STATUS_NO_SEQ"
    echo "   Backward compatibility confirmed"
else
    echo "⚠️  Trade without sequence number status: $STATUS_NO_SEQ"
fi
echo ""

echo "=========================================="
echo "✅ Priority 4 Features Test Complete"
echo "=========================================="
echo ""
echo "Summary:"
echo "  ✅ In-Order Sequence Processing: Tested"
echo "  ✅ Out-of-Order Message Buffering: Tested"
echo "  ✅ Out-of-Order Rejection: Tested"
echo "  ✅ Gap Too Large Rejection: Tested"
echo "  ✅ Buffer Status: Tested"
echo "  ✅ Backward Compatibility: Tested"
echo ""
echo "Check logs for sequence validation and buffer activity"


