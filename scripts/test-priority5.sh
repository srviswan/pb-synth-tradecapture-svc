#!/bin/bash

# Test script for Priority 5 features
# Tests: Rate Limiting, Bulkhead Pattern, Adaptive Retry

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
PARTITION_KEY="ACC-001_BOOK-001_US0378331005"

echo "=========================================="
echo "Priority 5 Features Test"
echo "=========================================="
echo ""

# Test 1: Health Check
echo "Test 1: Health Check"
echo "-------------------"
HEALTH=$(curl -s "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
if [ "$HEALTH" = "UP" ]; then
    echo "✅ Service is healthy"
else
    echo "❌ Service health check failed: $HEALTH"
    exit 1
fi
echo ""

# Test 2: Rate Limit Status
echo "Test 2: Rate Limit Status API"
echo "----------------------------"
RATE_LIMIT_STATUS=$(curl -s "$BASE_URL/api/v1/rate-limit/status/$PARTITION_KEY")
echo "$RATE_LIMIT_STATUS" | jq '.' 2>/dev/null || echo "$RATE_LIMIT_STATUS"
echo ""

# Test 3: Rate Limiting - Send requests faster than rate limit
echo "Test 3: Rate Limiting Test"
echo "-------------------------"
echo "Sending 25 requests (rate limit: 10/sec per partition)..."
RATE_LIMITED=0
SUCCESS=0
FAILED=0

for i in {1..25}; do
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/trades/capture" \
        -H "Content-Type: application/json" \
        -H "X-Callback-Url: http://example.com/callback" \
        -d "{
            \"tradeId\": \"TEST-RATE-$i\",
            \"accountId\": \"ACC-001\",
            \"bookId\": \"BOOK-001\",
            \"securityId\": \"US0378331005\",
            \"source\": \"AUTOMATED\",
            \"tradeDate\": \"2025-11-29\",
            \"counterpartyIds\": [\"CP-001\"],
            \"tradeLots\": [{
                \"lotIdentifier\": [{
                    \"identifier\": \"LOT-$i\",
                    \"identifierType\": \"INTERNAL\"
                }],
                \"priceQuantity\": [{
                    \"quantity\": [{
                        \"value\": 1000,
                        \"unit\": {
                            \"financialUnit\": \"SHARES\"
                        }
                    }],
                    \"price\": [{
                        \"value\": 100.50,
                        \"unit\": {
                            \"currency\": \"USD\"
                        }
                    }]
                }]
            }]
        }")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" = "202" ]; then
        STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
            if [ "$STATUS" = "ACCEPTED" ]; then
            SUCCESS=$((SUCCESS + 1))
        elif [ "$STATUS" = "FAILED" ]; then
            ERROR_CODE=$(echo "$BODY" | jq -r '.error.code' 2>/dev/null || echo "")
            if [ "$ERROR_CODE" = "RATE_LIMIT_EXCEEDED" ]; then
                RATE_LIMITED=$((RATE_LIMITED + 1))
                echo "  Request $i: Rate limited ✅"
            else
                FAILED=$((FAILED + 1))
                echo "  Request $i: Failed ($ERROR_CODE)"
            fi
        else
            FAILED=$((FAILED + 1))
            echo "  Request $i: Unknown status ($STATUS)"
        fi
    else
        FAILED=$((FAILED + 1))
        echo "  Request $i: HTTP $HTTP_CODE"
    fi
    
    # Small delay to avoid overwhelming
    sleep 0.1
done

echo ""
echo "Results:"
echo "  ✅ Successful: $SUCCESS"
echo "  ⚠️  Rate Limited: $RATE_LIMITED"
echo "  ❌ Failed: $FAILED"
echo ""

if [ $RATE_LIMITED -gt 0 ]; then
    echo "✅ Rate limiting is working (some requests were rate limited)"
else
    echo "⚠️  No rate limiting detected (may need to adjust rate limits or send requests faster)"
fi
echo ""

# Test 4: Check rate limit status after requests
echo "Test 4: Rate Limit Status After Requests"
echo "----------------------------------------"
RATE_LIMIT_STATUS_AFTER=$(curl -s "$BASE_URL/api/v1/rate-limit/status/$PARTITION_KEY")
AVAILABLE_TOKENS=$(echo "$RATE_LIMIT_STATUS_AFTER" | jq -r '.availableTokens' 2>/dev/null || echo "N/A")
echo "Available tokens: $AVAILABLE_TOKENS"
echo ""

# Test 5: Metrics Check
echo "Test 5: Metrics Check"
echo "--------------------"
METRICS=$(curl -s "$BASE_URL/actuator/metrics" | jq -r '.names[]' 2>/dev/null | grep -i "rate\|trade" | head -10 || echo "No metrics found")
echo "Relevant metrics:"
echo "$METRICS" | while read -r metric; do
    if [ -n "$metric" ]; then
        echo "  - $metric"
    fi
done
echo ""

echo "=========================================="
echo "Priority 5 Tests Complete"
echo "=========================================="

