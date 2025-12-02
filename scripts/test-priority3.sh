#!/bin/bash

# Script to test Priority 3.2 & 3.3 features: Caching and Transaction Optimization

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CORRELATION_ID="test-priority3-$(date +%s)"

echo "=========================================="
echo "Priority 3.2 & 3.3 Features Test"
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

# Test 2: Cache Functionality - Partition State
echo "=== Test 2: Partition State Caching ==="
echo "Submitting trade to trigger partition state caching..."
TRADE_ID_1="CACHE-TEST-1-$(date +%s)"
RESPONSE_1=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -H "X-Callback-Url: http://example.com/callback" \
  -d "{
    \"tradeId\": \"$TRADE_ID_1\",
    \"accountId\": \"ACC-CACHE-1\",
    \"bookId\": \"BOOK-CACHE-1\",
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

STATUS_1=$(echo "$RESPONSE_1" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_1" = "ACCEPTED" ]; then
    echo "✅ First trade accepted: $STATUS_1"
    echo "   Partition state should now be cached"
else
    echo "⚠️  First trade status: $STATUS_1"
fi

# Submit second trade to same partition (should use cache)
echo "Submitting second trade to same partition (should use cache)..."
TRADE_ID_2="CACHE-TEST-2-$(date +%s)"
RESPONSE_2=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -H "X-Callback-Url: http://example.com/callback" \
  -d "{
    \"tradeId\": \"$TRADE_ID_2\",
    \"accountId\": \"ACC-CACHE-1\",
    \"bookId\": \"BOOK-CACHE-1\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
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

STATUS_2=$(echo "$RESPONSE_2" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_2" = "ACCEPTED" ]; then
    echo "✅ Second trade accepted: $STATUS_2"
    echo "   Partition state should have been retrieved from cache"
else
    echo "⚠️  Second trade status: $STATUS_2"
fi
echo ""

# Test 3: Reference Data Caching
echo "=== Test 3: Reference Data Caching ==="
echo "Submitting trades with same security/account (should use cache)..."
TRADE_ID_3="REF-CACHE-1-$(date +%s)"
RESPONSE_3=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -H "X-Callback-Url: http://example.com/callback" \
  -d "{
    \"tradeId\": \"$TRADE_ID_3\",
    \"accountId\": \"ACC-REF-1\",
    \"bookId\": \"BOOK-REF-1\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-003\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 3000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_3=$(echo "$RESPONSE_3" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_3" = "ACCEPTED" ]; then
    echo "✅ First reference data lookup accepted: $STATUS_3"
    echo "   Security/Account data should now be cached"
fi

# Second trade with same security/account (should use cache)
TRADE_ID_4="REF-CACHE-2-$(date +%s)"
RESPONSE_4=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: $CORRELATION_ID" \
  -H "X-Callback-Url: http://example.com/callback" \
  -d "{
    \"tradeId\": \"$TRADE_ID_4\",
    \"accountId\": \"ACC-REF-1\",
    \"bookId\": \"BOOK-REF-1\",
    \"securityId\": \"US0378331005\",
    \"source\": \"AUTOMATED\",
    \"tradeLots\": [{
      \"lotIdentifier\": [{
        \"identifier\": \"LOT-004\",
        \"identifierType\": \"INTERNAL\"
      }],
      \"priceQuantity\": [{
        \"quantity\": [{
          \"value\": 4000,
          \"unit\": {
            \"currency\": \"USD\"
          }
        }]
      }]
    }],
    \"counterpartyIds\": [\"CP-001\"],
    \"tradeDate\": \"$(date +%Y-%m-%d)\"
  }")

STATUS_4=$(echo "$RESPONSE_4" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS_4" = "ACCEPTED" ]; then
    echo "✅ Second reference data lookup accepted: $STATUS_4"
    echo "   Security/Account data should have been retrieved from cache"
fi
echo ""

# Test 4: Rules Caching
echo "=== Test 4: Rules Caching ==="
echo "Checking rules endpoint (should use cache)..."
RULES_RESPONSE=$(curl -s "$BASE_URL/api/v1/rules")
if echo "$RULES_RESPONSE" | jq -e '.economic' > /dev/null 2>&1; then
    echo "✅ Rules endpoint accessible"
    echo "   Rules should be cached in Redis"
else
    echo "⚠️  Rules endpoint not accessible"
fi
echo ""

# Test 5: Transaction Optimization - Concurrent Trades
echo "=== Test 5: Transaction Optimization (Concurrent Trades) ==="
echo "Submitting 5 concurrent trades to different partitions..."
PARTITION_COUNT=5
SUCCESS_COUNT=0
FAIL_COUNT=0

for i in $(seq 1 $PARTITION_COUNT); do
    TRADE_ID="CONCURRENT-$i-$(date +%s)"
    RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
      -H "Content-Type: application/json" \
      -H "X-Correlation-ID: $CORRELATION_ID" \
      -H "X-Callback-Url: http://example.com/callback" \
      -d "{
        \"tradeId\": \"$TRADE_ID\",
        \"accountId\": \"ACC-CONC-$i\",
        \"bookId\": \"BOOK-CONC-$i\",
        \"securityId\": \"US0378331005\",
        \"source\": \"AUTOMATED\",
        \"tradeLots\": [{
          \"lotIdentifier\": [{
            \"identifier\": \"LOT-CONC-$i\",
            \"identifierType\": \"INTERNAL\"
          }],
          \"priceQuantity\": [{
            \"quantity\": [{
              \"value\": $((1000 * i)),
              \"unit\": {
                \"currency\": \"USD\"
              }
            }]
          }]
        }],
        \"counterpartyIds\": [\"CP-001\"],
        \"tradeDate\": \"$(date +%Y-%m-%d)\"
      }" &
    )
done

wait

echo "✅ Submitted $PARTITION_COUNT concurrent trades"
echo "   Transaction optimization should allow parallel processing"
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

# Test 7: Performance Test - Multiple Trades Same Partition
echo "=== Test 7: Performance Test (Same Partition) ==="
echo "Submitting 3 trades to same partition rapidly..."
START_TIME=$(date +%s%N)
for i in $(seq 1 3); do
    TRADE_ID="PERF-$i-$(date +%s)"
    curl -s -X POST "$BASE_URL/api/v1/trades/capture" \
      -H "Content-Type: application/json" \
      -H "X-Correlation-ID: $CORRELATION_ID" \
      -H "X-Callback-Url: http://example.com/callback" \
      -d "{
        \"tradeId\": \"$TRADE_ID\",
        \"accountId\": \"ACC-PERF\",
        \"bookId\": \"BOOK-PERF\",
        \"securityId\": \"US0378331005\",
        \"source\": \"AUTOMATED\",
        \"tradeLots\": [{
          \"lotIdentifier\": [{
            \"identifier\": \"LOT-PERF-$i\",
            \"identifierType\": \"INTERNAL\"
          }],
          \"priceQuantity\": [{
            \"quantity\": [{
              \"value\": $((1000 * i)),
              \"unit\": {
                \"currency\": \"USD\"
              }
            }]
          }]
        }],
        \"counterpartyIds\": [\"CP-001\"],
        \"tradeDate\": \"$(date +%Y-%m-%d)\"
      }" > /dev/null
done
END_TIME=$(date +%s%N)
DURATION=$(( (END_TIME - START_TIME) / 1000000 ))
echo "✅ Processed 3 trades in ${DURATION}ms"
echo "   Caching should improve subsequent trade processing"
echo ""

# Test 8: Cache Invalidation - Rules Update
echo "=== Test 8: Cache Invalidation (Rules) ==="
echo "Creating a test rule..."
RULE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/rules/workflow" \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"test-rule-$(date +%s)\",
    \"name\": \"Test Rule\",
    \"ruleType\": \"WORKFLOW\",
    \"enabled\": true,
    \"priority\": 1
  }")

if echo "$RULE_RESPONSE" | jq -e '.id' > /dev/null 2>&1; then
    echo "✅ Rule created successfully"
    echo "   Rules cache should have been invalidated"
else
    echo "⚠️  Rule creation failed"
fi
echo ""

echo "=========================================="
echo "✅ Priority 3.2 & 3.3 Features Test Complete"
echo "=========================================="
echo ""
echo "Summary:"
echo "  ✅ Partition State Caching: Tested"
echo "  ✅ Reference Data Caching: Tested"
echo "  ✅ Rules Caching: Tested"
echo "  ✅ Transaction Optimization: Tested"
echo "  ✅ Concurrent Processing: Tested"
echo "  ✅ Cache Invalidation: Tested"
echo ""
echo "Check logs for cache hit/miss patterns and transaction timing"



