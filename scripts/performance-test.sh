#!/bin/bash

# Performance Test Script for Trade Capture Service
# This script tests the service running in Docker

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
RESULTS_DIR="${RESULTS_DIR:-./performance-results}"

mkdir -p "$RESULTS_DIR"

echo "=========================================="
echo "Trade Capture Service Performance Tests"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Results Directory: $RESULTS_DIR"
echo ""

# Create a test trade request
create_trade_request() {
    local trade_id=$1
    local account_id=$2
    local book_id=$3
    cat <<EOF
{
  "tradeId": "$trade_id",
  "accountId": "$account_id",
  "bookId": "$book_id",
  "securityId": "US0378331005",
  "source": "AUTOMATED",
  "tradeDate": "$(date +%Y-%m-%d)",
  "counterpartyIds": ["PARTY-A", "PARTY-B"],
  "tradeLots": [
    {
      "lotIdentifier": [
        {
          "identifier": "LOT-001",
          "identifierType": "INTERNAL"
        }
      ],
      "priceQuantity": [
        {
          "quantity": [
            {
              "value": 10000,
              "unit": {
                "financialUnit": "Shares"
              }
            }
          ],
          "price": [
            {
              "value": 150.25,
              "unit": {
                "currency": "USD"
              }
            }
          ]
        }
      ]
    }
  ],
  "metadata": {
    "test": true,
    "sourceSystem": "performance-test"
  }
}
EOF
}

# Process a single trade and return latency
process_trade() {
    local trade_id=$1
    local account_id=$2
    local book_id=$3
    local idempotency_key="${trade_id}-$(uuidgen | tr -d '-' | head -c 16)"
    
    local start_time=$(date +%s%N)
    local response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/trades/capture" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idempotency_key" \
        -d "$(create_trade_request "$trade_id" "$account_id" "$book_id")" 2>&1)
    
    local end_time=$(date +%s%N)
    local http_code=$(echo "$response" | tail -n1)
    local latency_ms=$(( (end_time - start_time) / 1000000 ))
    
    # Check for success (200, 201) or duplicate (which is also acceptable for performance testing)
    if [ "$http_code" -eq 200 ] || [ "$http_code" -eq 201 ]; then
        echo "$latency_ms"
        return 0
    elif [ "$http_code" -eq 409 ]; then
        # Duplicate trade - count as success for performance testing
        echo "$latency_ms"
        return 0
    else
        # Log error for debugging
        if [ "$http_code" != "200" ] && [ "$http_code" != "201" ] && [ "$http_code" != "409" ]; then
            echo "Error: HTTP $http_code for trade $trade_id" >&2
        fi
        echo "-1"
        return 1
    fi
}

# Calculate percentiles
calculate_percentile() {
    local file=$1
    local percentile=$2
    local count=$(wc -l < "$file")
    local index=$(( (count * percentile) / 100 ))
    if [ $index -eq 0 ]; then
        index=1
    fi
    sort -n "$file" | sed -n "${index}p"
}

# Test 1: Latency Test (100 trades)
echo "=== Test 1: Latency Test ==="
echo "Processing 100 trades to measure latency..."
LATENCY_FILE="$RESULTS_DIR/latency.txt"
> "$LATENCY_FILE"

success=0
failures=0
# Use multiple partitions for latency test to better simulate real-world scenarios
num_partitions=5

for i in $(seq 1 100); do
    trade_id="LATENCY-TRADE-$(date +%s)-$i"
    # Distribute across partitions
    partition_num=$((i % num_partitions))
    account_id="ACC-LAT-$partition_num"
    book_id="BOOK-LAT-$partition_num"
    latency=$(process_trade "$trade_id" "$account_id" "$book_id")
    if [ "$latency" -ge 0 ]; then
        echo "$latency" >> "$LATENCY_FILE"
        ((success++))
    else
        ((failures++))
    fi
done

if [ $success -gt 0 ]; then
    p50=$(calculate_percentile "$LATENCY_FILE" 50)
    p95=$(calculate_percentile "$LATENCY_FILE" 95)
    p99=$(calculate_percentile "$LATENCY_FILE" 99)
    avg=$(awk '{sum+=$1; count++} END {print int(sum/count)}' "$LATENCY_FILE")
    max=$(sort -n "$LATENCY_FILE" | tail -1)
    
    echo "Results:"
    echo "  Success: $success"
    echo "  Failures: $failures"
    echo "  Latency (ms):"
    echo "    P50: $p50"
    echo "    P95: $p95"
    echo "    P99: $p99"
    echo "    Avg: $avg"
    echo "    Max: $max"
    echo ""
    
    if [ "$p95" -lt 500 ]; then
        echo "✓ P95 latency < 500ms: PASSED"
    else
        echo "✗ P95 latency >= 500ms: FAILED"
    fi
fi

# Test 2: Sustained Load Test (23 trades/sec for 30 seconds)
echo ""
echo "=== Test 2: Sustained Load Test ==="
echo "Target: 23 trades/sec for 30 seconds (690 trades total)"
SUSTAINED_FILE="$RESULTS_DIR/sustained.txt"
> "$SUSTAINED_FILE"

target_rate=23
duration=30
total_trades=$((target_rate * duration))
interval_ms=$((1000 / target_rate))

success=0
failures=0
start_time=$(date +%s)

# Use multiple partitions to distribute load
num_partitions=10

for i in $(seq 1 $total_trades); do
    trade_id="SUSTAINED-TRADE-$(date +%s)-$i"
    # Distribute across multiple partitions
    partition_num=$((i % num_partitions))
    account_id="ACC-SUS-$partition_num"
    book_id="BOOK-SUS-$partition_num"
    latency=$(process_trade "$trade_id" "$account_id" "$book_id")
    if [ "$latency" -ge 0 ]; then
        echo "$latency" >> "$SUSTAINED_FILE"
        ((success++))
    else
        ((failures++))
    fi
    
    if [ $i -lt $total_trades ]; then
        sleep $(echo "scale=3; $interval_ms/1000" | bc)
    fi
done

end_time=$(date +%s)
actual_duration=$((end_time - start_time))
actual_rate=$(echo "scale=2; $success / $actual_duration" | bc)

echo "Results:"
echo "  Success: $success"
echo "  Failures: $failures"
echo "  Actual rate: $actual_rate trades/sec"
echo "  Target rate: $target_rate trades/sec"
echo ""

if (( $(echo "$actual_rate >= $((target_rate * 80 / 100))" | bc -l) )); then
    echo "✓ Sustained load test: PASSED (>= 80% of target)"
else
    echo "✗ Sustained load test: FAILED (< 80% of target)"
fi

# Test 3: Burst Test (8x multiplier)
echo ""
echo "=== Test 3: Burst Capacity Test ==="
echo "Burst rate: 184 trades/sec for 5 seconds (920 trades total)"
BURST_FILE="$RESULTS_DIR/burst.txt"
> "$BURST_FILE"

burst_rate=184
burst_duration=5
total_burst=$((burst_rate * burst_duration))

success=0
failures=0
start_time=$(date +%s)

# Use multiple partitions to distribute load and reduce lock contention
num_partitions=20

# Send all trades as fast as possible in parallel
for i in $(seq 1 $total_burst); do
    trade_id="BURST-TRADE-$(date +%s)-$i"
    # Distribute across multiple partitions to reduce lock contention
    partition_num=$((i % num_partitions))
    account_id="ACC-BURST-$partition_num"
    book_id="BOOK-BURST-$partition_num"
    (
        latency=$(process_trade "$trade_id" "$account_id" "$book_id")
        if [ "$latency" -ge 0 ]; then
            echo "$latency" >> "$BURST_FILE"
        fi
    ) &
    
    # Limit concurrent requests
    if [ $((i % 50)) -eq 0 ]; then
        wait
    fi
done
wait

end_time=$(date +%s)
actual_duration=$((end_time - start_time))
success=$(wc -l < "$BURST_FILE")
actual_rate=$(echo "scale=2; $success / $actual_duration" | bc)

echo "Results:"
echo "  Success: $success"
echo "  Failures: $((total_burst - success))"
echo "  Actual rate: $actual_rate trades/sec"
echo "  Duration: $actual_duration seconds"
echo ""

if (( $(echo "$actual_rate >= $((burst_rate * 60 / 100))" | bc -l) )); then
    echo "✓ Burst capacity test: PASSED (>= 60% of burst rate)"
else
    echo "✗ Burst capacity test: FAILED (< 60% of burst rate)"
fi

# Test 4: Parallel Partitions Test
echo ""
echo "=== Test 4: Parallel Partitions Test ==="
echo "Processing 10 partitions in parallel (20 trades each = 200 total)"
PARALLEL_FILE="$RESULTS_DIR/parallel.txt"
> "$PARALLEL_FILE"

num_partitions=10
trades_per_partition=20

start_time=$(date +%s)

for p in $(seq 0 $((num_partitions - 1))); do
    (
        for t in $(seq 1 $trades_per_partition); do
            trade_id="PARALLEL-TRADE-P$p-T$t-$(date +%s)"
            latency=$(process_trade "$trade_id" "ACC-P$p" "BOOK-P$p")
            if [ "$latency" -ge 0 ]; then
                echo "$latency" >> "$PARALLEL_FILE"
            fi
        done
    ) &
done
wait

end_time=$(date +%s)
duration=$((end_time - start_time))
success=$(wc -l < "$PARALLEL_FILE")
throughput=$(echo "scale=2; $success / $duration" | bc)

echo "Results:"
echo "  Total trades: $((num_partitions * trades_per_partition))"
echo "  Successful: $success"
echo "  Duration: $duration seconds"
echo "  Throughput: $throughput trades/sec"
echo ""

if [ $success -ge $((num_partitions * trades_per_partition * 90 / 100)) ]; then
    echo "✓ Parallel partitions test: PASSED (>= 90% success rate)"
else
    echo "✗ Parallel partitions test: FAILED (< 90% success rate)"
fi

echo ""
echo "=========================================="
echo "Performance Test Summary"
echo "=========================================="
echo "Results saved to: $RESULTS_DIR"
echo ""

