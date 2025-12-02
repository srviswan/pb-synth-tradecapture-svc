#!/bin/bash

# Test rate limiting with concurrent requests
# Sends multiple requests in parallel to test rate limiting

BASE_URL="${BASE_URL:-http://localhost:8080}"
PARTITION_KEY="ACC-001_BOOK-001_US0378331005"

echo "=========================================="
echo "Rate Limiting Test - Concurrent Requests"
echo "=========================================="
echo ""

# Create a test request payload
create_request() {
    local trade_id=$1
    cat <<EOF
{
  "tradeId": "$trade_id",
  "accountId": "ACC-001",
  "bookId": "BOOK-001",
  "securityId": "US0378331005",
  "source": "AUTOMATED",
  "tradeDate": "2025-11-29",
  "counterpartyIds": ["CP-001"],
  "tradeLots": [{
    "lotIdentifier": [{
      "identifier": "LOT-$trade_id",
      "identifierType": "INTERNAL"
    }],
    "priceQuantity": [{
      "quantity": [{
        "value": 1000,
        "unit": {
          "financialUnit": "SHARES"
        }
      }],
      "price": [{
        "value": 100.50,
        "unit": {
          "currency": "USD"
        }
      }]
    }]
  }]
}
EOF
}

# Send requests concurrently
echo "Sending 25 concurrent requests (rate limit: 10/sec per partition)..."
echo ""

RATE_LIMITED=0
SUCCESS=0
FAILED=0

# Send requests in parallel
for i in {1..25}; do
    (
        REQUEST_BODY=$(create_request "RATE-TEST-$i")
        RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/trades/capture" \
            -H "Content-Type: application/json" \
            -d "$REQUEST_BODY")
        
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | sed '$d')
        
        if [ "$HTTP_CODE" = "200" ]; then
            STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
            if [ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "BUFFERED" ]; then
                echo "  Request $i: ✅ Success"
                echo "SUCCESS" > "/tmp/rate_test_$i.result"
            elif [ "$STATUS" = "FAILED" ]; then
                ERROR_CODE=$(echo "$BODY" | jq -r '.error.code' 2>/dev/null || echo "")
                if [ "$ERROR_CODE" = "RATE_LIMIT_EXCEEDED" ]; then
                    echo "  Request $i: ⚠️  Rate Limited"
                    echo "RATE_LIMITED" > "/tmp/rate_test_$i.result"
                else
                    echo "  Request $i: ❌ Failed ($ERROR_CODE)"
                    echo "FAILED" > "/tmp/rate_test_$i.result"
                fi
            else
                echo "  Request $i: ❓ Unknown status ($STATUS)"
                echo "FAILED" > "/tmp/rate_test_$i.result"
            fi
        else
            echo "  Request $i: ❌ HTTP $HTTP_CODE"
            echo "FAILED" > "/tmp/rate_test_$i.result"
        fi
    ) &
done

# Wait for all requests to complete
wait

# Count results
for i in {1..25}; do
    if [ -f "/tmp/rate_test_$i.result" ]; then
        RESULT=$(cat "/tmp/rate_test_$i.result")
        case "$RESULT" in
            "SUCCESS")
                SUCCESS=$((SUCCESS + 1))
                ;;
            "RATE_LIMITED")
                RATE_LIMITED=$((RATE_LIMITED + 1))
                ;;
            *)
                FAILED=$((FAILED + 1))
                ;;
        esac
        rm "/tmp/rate_test_$i.result"
    fi
done

echo ""
echo "=========================================="
echo "Results:"
echo "  ✅ Successful: $SUCCESS"
echo "  ⚠️  Rate Limited: $RATE_LIMITED"
echo "  ❌ Failed: $FAILED"
echo "=========================================="

if [ $RATE_LIMITED -gt 0 ]; then
    echo ""
    echo "✅ Rate limiting is working! Some requests were rate limited."
else
    echo ""
    echo "⚠️  No rate limiting detected."
    echo "   This could mean:"
    echo "   - Requests were processed fast enough to not exceed rate limit"
    echo "   - Rate limit tokens refilled between requests"
    echo "   - Rate limiting is disabled"
fi

# Check rate limit status
echo ""
echo "Rate Limit Status:"
curl -s "$BASE_URL/api/v1/rate-limit/status/$PARTITION_KEY" | jq '.' 2>/dev/null || echo "Failed to get status"



