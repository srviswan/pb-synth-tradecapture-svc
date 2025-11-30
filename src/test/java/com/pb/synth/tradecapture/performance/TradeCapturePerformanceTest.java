package com.pb.synth.tradecapture.performance;

import com.pb.synth.tradecapture.testutil.TradeCaptureRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests to validate 2M trades/day capacity, peak load, burst handling, and latency targets.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "test-mocked"})
@DisplayName("Trade Capture Performance Tests")
class TradeCapturePerformanceTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    /**
     * Create a test trade request with unique trade ID.
     */
    private Map<String, Object> createTestTrade(String tradeId, String accountId, String bookId) {
        return new TradeCaptureRequestBuilder()
            .withTradeId(tradeId)
            .withAccountId(accountId)
            .withBookId(bookId)
            .withSecurityId("US0378331005")
            .withSource("AUTOMATED")
            .withTradeDate(LocalDate.now())
            .addCounterpartyId("PARTY-A")
            .addCounterpartyId("PARTY-B")
            .addTradeLot(createTradeLot())
            .addMetadata("test", true)
            .addMetadata("sourceSystem", "performance-test")
            .build();
    }

    private Map<String, Object> createTradeLot() {
        Map<String, Object> lot = new HashMap<>();
        
        // Lot identifier
        List<Map<String, Object>> identifiers = new ArrayList<>();
        Map<String, Object> identifier = new HashMap<>();
        identifier.put("identifier", "LOT-001");
        identifier.put("identifierType", "INTERNAL");
        identifiers.add(identifier);
        lot.put("lotIdentifier", identifiers);
        
        // Price quantity
        List<Map<String, Object>> priceQuantities = new ArrayList<>();
        Map<String, Object> priceQuantity = new HashMap<>();
        
        // Quantity
        List<Map<String, Object>> quantities = new ArrayList<>();
        Map<String, Object> quantity = new HashMap<>();
        quantity.put("value", 10000);
        Map<String, Object> quantityUnit = new HashMap<>();
        quantityUnit.put("financialUnit", "Shares");
        quantity.put("unit", quantityUnit);
        quantities.add(quantity);
        priceQuantity.put("quantity", quantities);
        
        // Price
        List<Map<String, Object>> prices = new ArrayList<>();
        Map<String, Object> price = new HashMap<>();
        price.put("value", 150.25);
        Map<String, Object> priceUnit = new HashMap<>();
        priceUnit.put("currency", "USD");
        price.put("unit", priceUnit);
        prices.add(price);
        priceQuantity.put("price", prices);
        
        priceQuantities.add(priceQuantity);
        lot.put("priceQuantity", priceQuantities);
        
        return lot;
    }

    /**
     * Process a single trade and return latency in milliseconds.
     */
    private long processTrade(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/trades/capture",
                entity,
                Map.class
            );
            long latency = System.currentTimeMillis() - startTime;
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                System.err.println("Trade failed: " + response.getBody());
                return -1; // Indicate failure
            }
            
            return latency;
        } catch (Exception e) {
            System.err.println("Error processing trade: " + e.getMessage());
            return -1;
        }
    }

    @Test
    @DisplayName("should handle sustained load of 23 trades/sec (2M trades/day average)")
    void should_HandleSustainedLoad_When_23TradesPerSecond() throws InterruptedException {
        // Given
        int targetTradesPerSecond = 23; // Average from 2M trades/day
        int durationSeconds = 30; // Run for 30 seconds
        int totalTrades = targetTradesPerSecond * durationSeconds;
        int intervalMs = 1000 / targetTradesPerSecond; // ~43ms between trades

        System.out.println("\n=== Sustained Load Test ===");
        System.out.println("Target: " + targetTradesPerSecond + " trades/sec for " + durationSeconds + " seconds");
        System.out.println("Total trades: " + totalTrades);

        // When
        List<Long> latencies = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTrades; i++) {
            String tradeId = "PERF-TRADE-" + System.currentTimeMillis() + "-" + i;
            Map<String, Object> request = createTestTrade(tradeId, "ACC-001", "BOOK-001");
            
            long latency = processTrade(request);
            if (latency >= 0) {
                latencies.add(latency);
                successCount++;
            } else {
                failureCount++;
            }

            // Rate limiting: wait between trades
            if (i < totalTrades - 1) {
                Thread.sleep(intervalMs);
            }
        }

        long endTime = System.currentTimeMillis();
        long actualDuration = (endTime - startTime) / 1000;
        double actualRate = successCount / (double) actualDuration;

        // Then
        System.out.println("Results:");
        System.out.println("  Success: " + successCount);
        System.out.println("  Failures: " + failureCount);
        System.out.println("  Actual rate: " + String.format("%.2f", actualRate) + " trades/sec");
        System.out.println("  Target rate: " + targetTradesPerSecond + " trades/sec");
        
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long p50 = latencies.get((int) (latencies.size() * 0.50));
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));
            long avg = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
            long max = latencies.get(latencies.size() - 1);

            System.out.println("  Latency (ms):");
            System.out.println("    P50: " + p50);
            System.out.println("    P95: " + p95);
            System.out.println("    P99: " + p99);
            System.out.println("    Avg: " + avg);
            System.out.println("    Max: " + max);
        }

        assertThat(actualRate).isGreaterThanOrEqualTo(targetTradesPerSecond * 0.8); // 80% of target
        assertThat(failureCount).isLessThan((int) (totalTrades * 0.01)); // Less than 1% failures
    }

    @Test
    @DisplayName("should handle peak load of 186 trades/sec")
    void should_HandlePeakLoad_When_186TradesPerSecond() throws InterruptedException {
        // Given
        int peakTradesPerSecond = 186;
        int durationSeconds = 10;
        int totalTrades = peakTradesPerSecond * durationSeconds;
        int intervalMs = 1000 / peakTradesPerSecond; // ~5ms between trades

        System.out.println("\n=== Peak Load Test ===");
        System.out.println("Target: " + peakTradesPerSecond + " trades/sec for " + durationSeconds + " seconds");
        System.out.println("Total trades: " + totalTrades);

        // When
        List<Long> latencies = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTrades; i++) {
            String tradeId = "PEAK-TRADE-" + System.currentTimeMillis() + "-" + i;
            Map<String, Object> request = createTestTrade(tradeId, "ACC-002", "BOOK-002");
            
            long latency = processTrade(request);
            if (latency >= 0) {
                latencies.add(latency);
                successCount++;
            } else {
                failureCount++;
            }

            if (i < totalTrades - 1 && intervalMs > 0) {
                Thread.sleep(intervalMs);
            }
        }

        long endTime = System.currentTimeMillis();
        long actualDuration = (endTime - startTime) / 1000;
        double actualRate = successCount / (double) actualDuration;

        // Then
        System.out.println("Results:");
        System.out.println("  Success: " + successCount);
        System.out.println("  Failures: " + failureCount);
        System.out.println("  Actual rate: " + String.format("%.2f", actualRate) + " trades/sec");
        
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            System.out.println("  P95 Latency: " + p95 + " ms");
        }

        assertThat(actualRate).isGreaterThanOrEqualTo(peakTradesPerSecond * 0.7); // 70% of peak
        assertThat(failureCount).isLessThan((int) (totalTrades * 0.05)); // Less than 5% failures
    }

    @Test
    @DisplayName("should handle burst capacity of 8x multiplier")
    void should_HandleBurst_When_8xMultiplier() throws InterruptedException {
        // Given
        int averageRate = 23; // trades/sec
        int burstMultiplier = 8;
        int burstRate = averageRate * burstMultiplier; // 184 trades/sec
        int burstDurationSeconds = 5;
        int totalTrades = burstRate * burstDurationSeconds;

        System.out.println("\n=== Burst Capacity Test ===");
        System.out.println("Burst rate: " + burstRate + " trades/sec for " + burstDurationSeconds + " seconds");
        System.out.println("Total trades: " + totalTrades);

        // When - Send all trades as fast as possible
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTrades; i++) {
            final int index = i;
            String tradeId = "BURST-TRADE-" + System.currentTimeMillis() + "-" + index;
            Map<String, Object> request = createTestTrade(tradeId, "ACC-003", "BOOK-003");
            
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                return processTrade(request);
            }, executor);
            futures.add(future);
        }

        // Wait for all to complete
        List<Long> latencies = futures.stream()
            .map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return -1L;
                }
            })
            .filter(l -> l >= 0)
            .collect(Collectors.toList());

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long actualDuration = (endTime - startTime) / 1000;
        int successCount = latencies.size();
        int failureCount = totalTrades - successCount;
        double actualRate = successCount / (double) actualDuration;

        // Then
        System.out.println("Results:");
        System.out.println("  Success: " + successCount);
        System.out.println("  Failures: " + failureCount);
        System.out.println("  Actual rate: " + String.format("%.2f", actualRate) + " trades/sec");
        System.out.println("  Duration: " + actualDuration + " seconds");
        
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));
            System.out.println("  P95 Latency: " + p95 + " ms");
            System.out.println("  P99 Latency: " + p99 + " ms");
        }

        assertThat(actualRate).isGreaterThanOrEqualTo(burstRate * 0.6); // 60% of burst rate
        assertThat(failureCount).isLessThan((int) (totalTrades * 0.10)); // Less than 10% failures
    }

    @Test
    @DisplayName("should maintain P95 latency under 500ms")
    void should_MaintainLatency_When_Under500msP95() {
        // Given
        int numTrades = 100;
        System.out.println("\n=== Latency Test ===");
        System.out.println("Processing " + numTrades + " trades to measure latency");

        // When
        List<Long> latencies = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < numTrades; i++) {
            String tradeId = "LATENCY-TRADE-" + System.currentTimeMillis() + "-" + i;
            Map<String, Object> request = createTestTrade(tradeId, "ACC-004", "BOOK-004");
            
            long latency = processTrade(request);
            if (latency >= 0) {
                latencies.add(latency);
                successCount++;
            }
        }

        // Then
        assertThat(successCount).isGreaterThan(0);
        
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long p50 = latencies.get((int) (latencies.size() * 0.50));
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));
            long avg = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
            long max = latencies.get(latencies.size() - 1);

            System.out.println("Results:");
            System.out.println("  Success: " + successCount);
            System.out.println("  Latency (ms):");
            System.out.println("    P50: " + p50);
            System.out.println("    P95: " + p95);
            System.out.println("    P99: " + p99);
            System.out.println("    Avg: " + avg);
            System.out.println("    Max: " + max);

            assertThat(p95).isLessThan(500); // P95 < 500ms
        }
    }

    @Test
    @DisplayName("should process different partitions in parallel")
    void should_ProcessPartitions_When_Parallel() throws InterruptedException {
        // Given
        int numPartitions = 10;
        int tradesPerPartition = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numPartitions);

        System.out.println("\n=== Parallel Partition Test ===");
        System.out.println("Processing " + numPartitions + " partitions in parallel");
        System.out.println("Trades per partition: " + tradesPerPartition);

        // When
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numPartitions; i++) {
            final int partitionIndex = i;
            String accountId = "ACC-P" + partitionIndex;
            String bookId = "BOOK-P" + partitionIndex;
            
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                int successCount = 0;
                for (int j = 0; j < tradesPerPartition; j++) {
                    String tradeId = "PARALLEL-TRADE-P" + partitionIndex + "-" + j + "-" + System.currentTimeMillis();
                    Map<String, Object> request = createTestTrade(tradeId, accountId, bookId);
                    long latency = processTrade(request);
                    if (latency >= 0) {
                        successCount++;
                    }
                }
                return successCount;
            }, executor);
            futures.add(future);
        }

        // Wait for all partitions
        List<Integer> results = futures.stream()
            .map(f -> {
                try {
                    return f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return 0;
                }
            })
            .collect(Collectors.toList());

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        int totalSuccess = results.stream().mapToInt(Integer::intValue).sum();
        int totalTrades = numPartitions * tradesPerPartition;

        // Then
        System.out.println("Results:");
        System.out.println("  Total trades: " + totalTrades);
        System.out.println("  Successful: " + totalSuccess);
        System.out.println("  Duration: " + duration + " seconds");
        System.out.println("  Throughput: " + String.format("%.2f", totalSuccess / (double) duration) + " trades/sec");

        assertThat(totalSuccess).isGreaterThanOrEqualTo((int) (totalTrades * 0.9)); // 90% success rate
    }
}
