# Trade Capture Service - End-to-End Sequence Diagram

## Overview

This document provides comprehensive sequence diagrams showing the complete end-to-end flow of the Trade Capture Service, including:
- **Async API Flow**: REST API → Message Queue → Processing → Webhook Callback
- **Message Queue Flow**: Direct message consumption from Kafka/Solace
- **Error Handling**: Retry, deadlock handling, and DLQ
- **Parallel Processing**: Multiple partitions processing concurrently

## Async API Flow (REST API with Webhook Callback)

This is the primary flow when clients submit trades via REST API with a callback URL.

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant JSS as JobStatusService
    participant TPS as TradePublishingService
    participant MQ as Message Queue<br/>(Kafka/Solace)
    participant Consumer as MessageConsumer<br/>(Kafka/Solace)
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant Redis
    participant IS as IdempotencyService
    participant DB as SQL Server
    participant ES as EnrichmentService
    participant SMS as SecurityMasterServiceClient
    participant ASC as AccountServiceClient
    participant RE as RulesEngine
    participant VS as ValidationService
    participant AWS as ApprovalWorkflowServiceClient
    participant SMSvc as StateManagementService
    participant SBS as SwapBlotterService
    participant WS as WebhookService
    participant Callback as Client Webhook<br/>Endpoint

    Note over Client,Callback: Async API Flow with Webhook Callback

    Client->>API: POST /api/v1/trades/capture<br/>Headers: X-Callback-Url<br/>Body: TradeCaptureRequest
    API->>JSS: createJob(null, tradeId, "REST_API")
    JSS->>DB: INSERT job_status<br/>(status: PENDING)
    DB-->>JSS: Job created
    JSS-->>API: jobId: "abc-123"
    
    API->>TPS: publishTrade(request, jobId, "REST_API", callbackUrl)
    TPS->>TPS: Convert to Protobuf<br/>Add metadata: job_id, callback_url, source_api
    TPS->>MQ: Publish to queue<br/>(topic: trade/capture/input)
    MQ-->>TPS: Message published
    TPS-->>API: Published successfully
    
    API-->>Client: 202 Accepted<br/>{jobId, status: "ACCEPTED", statusUrl}
    
    Note over MQ,Callback: Async Processing (Background)
    
    MQ->>Consumer: Consume message
    Consumer->>TMP: processMessage(request, metadata)
    
    Note over TMP,Redis: Step 1: Job Status Update
    TMP->>JSS: updateJobStatus(jobId, PROCESSING, 50)
    JSS->>DB: UPDATE job_status<br/>(status: PROCESSING, progress: 50)
    DB-->>JSS: Updated
    JSS-->>TMP: Status updated
    
    Note over TMP,Redis: Step 2: Partition Locking
    TMP->>TCS: processTrade(request)
    TCS->>PLS: acquireLock(partitionKey)
    PLS->>Redis: SETNX lock:partition:{key}
    Redis-->>PLS: Lock acquired
    PLS-->>TCS: Lock acquired
    
    Note over TCS,DB: Step 3: Idempotency Check
    TCS->>IS: checkDuplicate(request)
    IS->>Redis: GET idempotency:{key}
    alt Cache Hit - Completed
        Redis-->>IS: Cached result (COMPLETED)
        IS->>DB: SELECT swap_blotter WHERE trade_id = ?
        DB-->>IS: SwapBlotter
        IS-->>TCS: Return cached SwapBlotter
        TCS->>PLS: releaseLock(partitionKey)
        TCS-->>TMP: DUPLICATE response
        TMP->>JSS: updateJobStatus(jobId, COMPLETED, 100)
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, response)
        WS->>Callback: POST webhook payload<br/>(status: COMPLETED, cached result)
        Callback-->>WS: 200 OK
        WS-->>TMP: Webhook sent
        TMP-->>Consumer: Processing complete
    else Cache Miss - New Trade
        Redis-->>IS: Not found
        IS->>DB: SELECT idempotency_record WHERE key = ?
        DB-->>IS: No record
        IS-->>TCS: Not duplicate, proceed
    end
    
    Note over TCS,DB: Step 4: Create Idempotency Record
    TCS->>IS: createIdempotencyRecord(request)
    IS->>DB: INSERT idempotency_record<br/>(status: PROCESSING)
    DB-->>IS: Record created
    IS->>Redis: SET idempotency:{key} = PROCESSING<br/>(TTL: 12 hours)
    Redis-->>IS: Cached
    IS-->>TCS: Record created
    
    Note over TCS,ASC: Step 5: Parallel Enrichment
    par Parallel Enrichment
        TCS->>ES: enrich(request)
        ES->>SMS: lookupSecurityAsync(securityId)
        SMS->>SMS: Mock/Real Service Call<br/>(with circuit breaker)
        SMS-->>ES: Security data
    and
        ES->>ASC: lookupAccountAsync(accountId, bookId)
        ASC->>ASC: Mock/Real Service Call<br/>(with circuit breaker)
        ASC-->>ES: Account data
    end
    ES-->>TCS: EnrichmentResult<br/>(status, enrichedData)
    
    Note over TCS,RE: Step 6: Build Initial SwapBlotter
    TCS->>TCS: buildInitialSwapBlotter(request, enrichmentResult)
    TCS->>TCS: SwapBlotter created<br/>(enrichmentStatus, workflowStatus: PENDING_APPROVAL)
    
    Note over TCS,RE: Step 7: Apply Rules
    TCS->>RE: applyRules(swapBlotter, tradeData)
    RE->>RE: Evaluate Economic Rules
    RE->>RE: Evaluate Non-Economic Rules
    RE->>RE: Evaluate Workflow Rules
    RE-->>TCS: Updated SwapBlotter<br/>(rulesApplied tracked)
    
    Note over TCS,VS: Step 8: Validation
    TCS->>VS: validate(request)
    VS->>VS: Validate ISIN, Account Status, etc.
    VS-->>TCS: Validation passed
    
    Note over TCS,AWS: Step 9: Approval Workflow
    alt Workflow Status = PENDING_APPROVAL
        TCS->>AWS: submitForApproval(swapBlotter)
        AWS->>AWS: Mock/Real Approval Service
        AWS-->>TCS: WorkflowStatus (APPROVED/REJECTED/PENDING)
        
        alt Rejected
            TCS->>IS: markFailed(idempotencyKey)
            TCS->>PLS: releaseLock(partitionKey)
            TCS-->>TMP: REJECTED response
            TMP->>JSS: updateJobStatus(jobId, FAILED, 100)
            TMP->>WS: sendWebhook(callbackUrl, jobStatus, null)
            WS->>Callback: POST webhook payload<br/>(status: FAILED, error)
            Callback-->>WS: 200 OK
        else Approved
            Note over TCS: Continue to Step 10
        end
    end
    
    Note over TCS,DB: Step 10: State Management
    TCS->>SMSvc: updateState(partitionKey, newState)
    SMSvc->>DB: SELECT partition_state<br/>WHERE partition_key = ?<br/>(PESSIMISTIC_WRITE lock)
    DB-->>SMSvc: Current state
    SMSvc->>SMSvc: Validate state transition
    SMSvc->>DB: UPDATE partition_state<br/>(positionState, stateJson, version++)
    DB-->>SMSvc: State updated
    SMSvc-->>TCS: State updated
    
    Note over TCS,DB: Step 11: Save SwapBlotter
    TCS->>SBS: saveSwapBlotter(swapBlotter)
    SBS->>DB: INSERT swap_blotter<br/>(tradeId, partitionKey, swapBlotterJson)
    DB-->>SBS: Blotter saved
    SBS-->>TCS: SwapBlotterEntity
    
    Note over TCS,DB: Step 12: Update Idempotency Record
    TCS->>IS: markCompleted(idempotencyKey, swapBlotterId)
    IS->>DB: UPDATE idempotency_record<br/>(status: COMPLETED, swapBlotterId)
    DB-->>IS: Record updated
    IS->>Redis: SET idempotency:{key} = COMPLETED<br/>(TTL: 12 hours)
    Redis-->>IS: Cached
    IS-->>TCS: Marked completed
    
    Note over TCS,Redis: Step 13: Release Lock
    TCS->>PLS: releaseLock(partitionKey)
    PLS->>Redis: DEL lock:partition:{key}
    Redis-->>PLS: Lock released
    PLS-->>TCS: Lock released
    
    Note over TMP,Callback: Step 14: Update Job Status & Send Webhook
    TCS-->>TMP: TradeCaptureResponse<br/>(status: SUCCESS, swapBlotter)
    TMP->>JSS: updateJobStatus(jobId, COMPLETED, 100, response)
    JSS->>DB: UPDATE job_status<br/>(status: COMPLETED, result: response)
    DB-->>JSS: Updated
    JSS-->>TMP: Status updated
    
    TMP->>WS: sendWebhook(callbackUrl, jobStatus, response)
    WS->>WS: Build webhook payload<br/>(jobId, status, tradeId, swapBlotter)
    WS->>Callback: POST https://client.com/webhooks/trade-complete<br/>Payload: {jobId, status: "COMPLETED", swapBlotter, ...}
    alt Webhook Success
        Callback-->>WS: 200 OK
        WS-->>TMP: Webhook sent successfully
    else Webhook Failure
        Callback-->>WS: 500 Internal Server Error
        WS->>WS: Retry with exponential backoff<br/>(up to 3 attempts)
        alt Retry Successful
            Callback-->>WS: 200 OK
            WS-->>TMP: Webhook sent (after retry)
        else All Retries Failed
            WS-->>TMP: Webhook failed (logged, job still completed)
        end
    end
    
    TMP-->>Consumer: Processing complete
    Consumer-->>MQ: Acknowledge message
    
    Note over Client,Callback: Client receives webhook notification<br/>Can also poll statusUrl for job status
```

## Direct Message Queue Flow (No API Call)

This flow occurs when trades are published directly to the message queue (e.g., from upstream systems).

```mermaid
sequenceDiagram
    participant Upstream as Upstream System
    participant MQ as Message Queue<br/>(Kafka/Solace)
    participant Router as SolaceMessageRouter<br/>(if Solace)
    participant Consumer as MessageConsumer<br/>(Kafka/Solace)
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant Redis
    participant IS as IdempotencyService
    participant DB as SQL Server
    participant ES as EnrichmentService
    participant SMS as SecurityMasterServiceClient
    participant ASC as AccountServiceClient
    participant RE as RulesEngine
    participant VS as ValidationService
    participant AWS as ApprovalWorkflowServiceClient
    participant SMSvc as StateManagementService
    participant SBS as SwapBlotterService
    participant Publisher as SwapBlotterPublisher

    Note over Upstream,Publisher: Direct Message Queue Flow

    Upstream->>MQ: Publish TradeCaptureMessage<br/>(topic: trade/capture/input)
    MQ-->>Upstream: Message published
    
    alt Solace with Router Enabled
        MQ->>Router: Consume from input topic
        Router->>Router: Extract partition key
        Router->>MQ: Republish to partition topic<br/>(trade/capture/input/{partitionKey})
        MQ->>Consumer: Consume from partition topic
    else Kafka or Solace without Router
        MQ->>Consumer: Consume from input topic
    end
    
    Consumer->>TMP: processMessage(request, metadata)
    
    Note over TMP: No jobId or callbackUrl<br/>(direct queue processing)
    
    Note over TMP,Redis: Processing Steps (Same as API Flow)
    TMP->>TCS: processTrade(request)
    TCS->>PLS: acquireLock(partitionKey)
    PLS->>Redis: SETNX lock:partition:{key}
    Redis-->>PLS: Lock acquired
    
    TCS->>IS: checkDuplicate(request)
    IS->>Redis: GET idempotency:{key}
    alt Duplicate
        Redis-->>IS: Cached result
        IS-->>TCS: Return cached SwapBlotter
        TCS->>PLS: releaseLock(partitionKey)
        TCS-->>TMP: DUPLICATE response
    else New Trade
        Redis-->>IS: Not found
        IS-->>TCS: Not duplicate, proceed
        
        Note over TCS,Publisher: Full Processing Flow
        TCS->>ES: enrich(request)
        ES->>SMS: lookupSecurityAsync(securityId)
        ES->>ASC: lookupAccountAsync(accountId, bookId)
        ES-->>TCS: EnrichmentResult
        
        TCS->>TCS: buildInitialSwapBlotter(request, enrichmentResult)
        TCS->>RE: applyRules(swapBlotter, tradeData)
        RE-->>TCS: Updated SwapBlotter
        
        TCS->>VS: validate(request)
        VS-->>TCS: Validation passed
        
        alt Workflow Status = PENDING_APPROVAL
            TCS->>AWS: submitForApproval(swapBlotter)
            AWS-->>TCS: WorkflowStatus
        end
        
        TCS->>SMSvc: updateState(partitionKey, newState)
        SMSvc->>DB: UPDATE partition_state
        SMSvc-->>TCS: State updated
        
        TCS->>SBS: saveSwapBlotter(swapBlotter)
        SBS->>DB: INSERT swap_blotter
        SBS-->>TCS: SwapBlotterEntity
        
        TCS->>IS: markCompleted(idempotencyKey, swapBlotterId)
        IS->>DB: UPDATE idempotency_record
        IS->>Redis: SET idempotency:{key} = COMPLETED
    end
    
    TCS->>PLS: releaseLock(partitionKey)
    PLS->>Redis: DEL lock:partition:{key}
    PLS-->>TCS: Lock released
    
    TCS-->>TMP: TradeCaptureResponse
    
    Note over TMP,Publisher: Publish Output (No Webhook)
    TMP->>Publisher: publish(swapBlotter)
    Publisher->>MQ: Publish to output topic<br/>(trade/capture/blotter)
    MQ-->>Publisher: Published
    
    TMP-->>Consumer: Processing complete
    Consumer-->>MQ: Acknowledge message
```

## Error Handling Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant TPS as TradePublishingService
    participant MQ as Message Queue
    participant Consumer as MessageConsumer
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant IS as IdempotencyService
    participant DB as SQL Server
    participant JSS as JobStatusService
    participant WS as WebhookService
    participant DLQ as DeadLetterQueue

    Note over Client,DLQ: Error Handling Scenarios

    alt Lock Acquisition Failed
        Client->>API: POST /trades/capture
        API->>TPS: publishTrade(request, jobId, ...)
        TPS->>MQ: Publish message
        MQ->>Consumer: Consume message
        Consumer->>TMP: processMessage(request)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock(partitionKey)
        PLS-->>TCS: Lock acquisition failed (timeout)
        TCS-->>TMP: FAILED (LOCK_ACQUISITION_FAILED)
        TMP->>JSS: updateJobStatus(jobId, FAILED, 100, error)
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, null)
        WS->>Client: POST webhook (status: FAILED)
        TMP->>DLQ: publishToDLQ(message, error)
        DLQ-->>TMP: Message sent to DLQ
    end
    
    alt Deadlock During Processing
        TMP->>TCS: processTrade(request)
        TCS->>DB: UPDATE partition_state
        DB-->>TCS: Deadlock error (1205)
        TCS->>TCS: DeadlockRetryAspect intercepts
        TCS->>TCS: Retry with REQUIRES_NEW transaction
        alt Retry Successful
            DB-->>TCS: Update successful
            TCS-->>TMP: Continue processing
        else Retry Exhausted
            TCS->>IS: markFailed(idempotencyKey)
            TCS-->>TMP: FAILED (PROCESSING_ERROR)
            TMP->>JSS: updateJobStatus(jobId, FAILED, 100, error)
            TMP->>WS: sendWebhook(callbackUrl, jobStatus, null)
            TMP->>DLQ: publishToDLQ(message, error)
        end
    end
    
    alt External Service Failure
        TCS->>ES: enrich(request)
        ES->>SMS: lookupSecurityAsync(securityId)
        SMS-->>ES: Circuit breaker open / Timeout
        SMS-->>ES: Fallback (empty Optional)
        ES-->>TCS: EnrichmentResult (status: PARTIAL)
        TCS->>TCS: Continue with partial enrichment
        TCS-->>TMP: SUCCESS (enrichmentStatus: PARTIAL)
        TMP->>JSS: updateJobStatus(jobId, COMPLETED, 100, response)
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, response)
        Note over WS: Webhook includes warning about partial enrichment
    end
    
    alt Validation Failure
        TCS->>VS: validate(request)
        VS-->>TCS: ValidationException
        TCS->>IS: markFailed(idempotencyKey)
        TCS-->>TMP: FAILED (VALIDATION_ERROR)
        TMP->>JSS: updateJobStatus(jobId, FAILED, 100, error)
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, null)
        WS->>Client: POST webhook (status: FAILED, error details)
    end
    
    alt Webhook Delivery Failure
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, response)
        WS->>Client: POST webhook
        Client-->>WS: 500 Internal Server Error
        WS->>WS: Retry attempt 1 (after 1s)
        Client-->>WS: 500 Internal Server Error
        WS->>WS: Retry attempt 2 (after 2s)
        Client-->>WS: 500 Internal Server Error
        WS->>WS: All retries exhausted
        WS-->>TMP: Webhook failed (logged)
        Note over TMP: Job status still updated<br/>Client can poll statusUrl
    end
```

## Parallel Processing Flow

```mermaid
sequenceDiagram
    participant MQ as Message Queue<br/>(Kafka/Solace)
    participant Consumer1 as Consumer Instance 1
    participant Consumer2 as Consumer Instance 2
    participant Consumer3 as Consumer Instance 3
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant Redis

    Note over MQ,Redis: Parallel Partition Processing

    par Partition A Processing (Instance 1)
        MQ->>Consumer1: Message (Partition A)
        Consumer1->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-001_BOOK-001_SEC-001")
        PLS->>Redis: Lock for Partition A
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-001_BOOK-001_SEC-001")
        TMP-->>Consumer1: Processing complete
    and Partition B Processing (Instance 2)
        MQ->>Consumer2: Message (Partition B)
        Consumer2->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-002_BOOK-002_SEC-002")
        PLS->>Redis: Lock for Partition B
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-002_BOOK-002_SEC-002")
        TMP-->>Consumer2: Processing complete
    and Partition C Processing (Instance 3)
        MQ->>Consumer3: Message (Partition C)
        Consumer3->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-003_BOOK-003_SEC-003")
        PLS->>Redis: Lock for Partition C
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-003_BOOK-003_SEC-003")
        TMP-->>Consumer3: Processing complete
    end
    
    Note over MQ,Redis: All partitions process in parallel<br/>Same partition processes sequentially
```

## Idempotency Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant TPS as TradePublishingService
    participant MQ as Message Queue
    participant Consumer as MessageConsumer
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant IS as IdempotencyService
    participant Redis
    participant DB as SQL Server
    participant JSS as JobStatusService
    participant WS as WebhookService

    Note over Client,WS: Idempotency Check Flow

    Client->>API: POST /trades/capture<br/>(Idempotency-Key: KEY-123)
    API->>TPS: publishTrade(request, jobId, ...)
    TPS->>MQ: Publish message
    MQ->>Consumer: Consume message
    Consumer->>TMP: processMessage(request, metadata)
    TMP->>TCS: processTrade(request)
    
    TCS->>IS: checkDuplicate(request)
    
    Note over IS,Redis: L1 Cache Check (Redis)
    IS->>Redis: GET idempotency:KEY-123
    alt Cache Hit - Completed
        Redis-->>IS: COMPLETED
        IS->>DB: SELECT swap_blotter WHERE trade_id = ?
        DB-->>IS: SwapBlotter
        IS-->>TCS: Return cached SwapBlotter
        TCS-->>TMP: DUPLICATE response
        TMP->>JSS: updateJobStatus(jobId, COMPLETED, 100, cachedResponse)
        TMP->>WS: sendWebhook(callbackUrl, jobStatus, cachedResponse)
        WS->>Client: POST webhook (cached result)
    else Cache Hit - Processing
        Redis-->>IS: PROCESSING
        IS-->>TCS: Trade still processing
        TCS-->>TMP: PENDING response
        TMP->>JSS: updateJobStatus(jobId, PROCESSING, 50)
        Note over TMP: Client should poll statusUrl
    else Cache Miss
        Redis-->>IS: Not found
        
        Note over IS,DB: L2 Cache Check (Database)
        IS->>DB: SELECT idempotency_record<br/>WHERE idempotency_key = ?<br/>AND archive_flag = 0
        alt Record Found - Completed
            DB-->>IS: Record (status: COMPLETED)
            IS->>Redis: SET idempotency:KEY-123 = COMPLETED<br/>(TTL: 12 hours)
            IS->>DB: SELECT swap_blotter WHERE trade_id = ?
            DB-->>IS: SwapBlotter
            IS-->>TCS: Return cached SwapBlotter
            TCS-->>TMP: DUPLICATE response
            TMP->>JSS: updateJobStatus(jobId, COMPLETED, 100, cachedResponse)
            TMP->>WS: sendWebhook(callbackUrl, jobStatus, cachedResponse)
        else Record Found - Processing
            DB-->>IS: Record (status: PROCESSING)
            IS->>Redis: SET idempotency:KEY-123 = PROCESSING<br/>(TTL: 12 hours)
            IS-->>TCS: Trade still processing
            TCS-->>TMP: PENDING response
        else New Trade
            DB-->>IS: No record found
            IS-->>TCS: Not duplicate, proceed
            TCS->>IS: createIdempotencyRecord(request)
            IS->>DB: INSERT idempotency_record<br/>(status: PROCESSING)
            IS->>Redis: SET idempotency:KEY-123 = PROCESSING<br/>(TTL: 12 hours)
            Note over TCS: Continue with trade processing...
        end
    end
```

## Solace Partition Routing Flow

```mermaid
sequenceDiagram
    participant Upstream as Upstream System
    participant Solace as Solace PubSub+
    participant Router as SolaceMessageRouter
    participant Consumer as SolaceTradeMessageConsumer
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService

    Note over Upstream,TCS: Solace Partition-Based Topic Routing

    Upstream->>Solace: Publish to single topic<br/>(trade/capture/input)
    Solace->>Router: Consume from input topic
    
    Router->>Router: Extract partition key<br/>from message
    Router->>Router: Build partition topic<br/>(trade/capture/input/{partitionKey})
    
    alt Partition Key: ACC-001_BOOK-001_SEC-001
        Router->>Solace: Republish to<br/>trade/capture/input/ACC-001_BOOK-001_SEC-001
    else Partition Key: ACC-002_BOOK-002_SEC-002
        Router->>Solace: Republish to<br/>trade/capture/input/ACC-002_BOOK-002_SEC-002
    end
    
    Solace->>Consumer: Consume from partition topics<br/>(wildcard: trade/capture/input/>)
    
    Note over Consumer,TCS: Messages automatically ordered per partition
    Consumer->>TMP: processMessage(request, metadata)
    TMP->>TCS: processTrade(request)
    TCS-->>TMP: TradeCaptureResponse
    TMP-->>Consumer: Processing complete
```

## Components Legend

### Controllers & API
- **TradeCaptureController**: REST API endpoint handler
- **ManualEntryController**: Manual trade entry endpoint
- **FileUploadController**: Batch file upload endpoint

### Services
- **TradeCaptureService**: Main orchestration service
- **TradePublishingService**: Publishes trades to message queue
- **TradeMessageProcessor**: Processes messages from queue
- **JobStatusService**: Tracks async job status
- **WebhookService**: Sends webhook callbacks
- **PartitionLockService**: Distributed locking using Redis/Hazelcast
- **IdempotencyService**: Duplicate detection and caching
- **EnrichmentService**: Parallel enrichment orchestration
- **SecurityMasterServiceClient**: Security data lookup (with circuit breaker)
- **AccountServiceClient**: Account/Book data lookup (with circuit breaker)
- **RulesEngine**: Rule evaluation and application
- **ValidationService**: Trade validation
- **ApprovalWorkflowServiceClient**: Approval workflow integration
- **StateManagementService**: CDM-compliant state management
- **SwapBlotterService**: Blotter persistence

### Messaging
- **TradeInputPublisher**: Interface for publishing trades (Kafka/Solace)
- **TradeInputPublisherFactory**: Factory for getting configured publisher
- **SolaceMessageRouter**: Routes messages to partition-specific topics
- **SwapBlotterPublisher**: Publishes processed blotters to output queue

### Infrastructure
- **Redis/Hazelcast**: Distributed locking and idempotency cache (L1)
- **SQL Server**: Persistent storage (idempotency records, swap blotter, partition state, job status)
- **Kafka/Solace**: Message queues for trade input and blotter output
- **DeadLetterQueue**: Failed message handling

## Key Design Patterns

1. **Async Processing**: All API requests are queued for async processing
2. **Webhook Callbacks**: Clients receive notifications via HTTP POST to callback URL
3. **Job Status Tracking**: Jobs tracked in database with status polling endpoint
4. **Partition-Based Processing**: Trades partitioned by `{accountId}_{bookId}_{securityId}`
5. **Distributed Locking**: Redis/Hazelcast-based locks ensure sequential processing per partition
6. **Two-Level Caching**: Redis (L1) + Database (L2) for idempotency checks
7. **Parallel Enrichment**: Concurrent calls to SecurityMaster and AccountService
8. **Circuit Breaker Pattern**: Resilience4j for external service calls
9. **Deadlock Retry**: Automatic retry with `REQUIRES_NEW` transactions
10. **Idempotency**: Prevents duplicate processing via idempotency keys
11. **Approval Workflow**: Conditional approval for manual trades
12. **State Management**: CDM-compliant state transitions with optimistic locking
13. **Solace Partition Routing**: Internal routing to partition-specific topics for ordering

## Webhook Payload Structure

```json
{
  "jobId": "abc-123-def-456",
  "status": "COMPLETED",
  "progress": 100,
  "message": "Trade processing completed",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:05Z",
  "tradeId": "TRADE-2024-001",
  "tradeStatus": "SUCCESS",
  "swapBlotter": {
    "tradeId": "TRADE-2024-001",
    "partitionKey": "ACC-001_BOOK-001_US0378331005",
    "enrichmentStatus": "COMPLETE",
    "workflowStatus": "APPROVED",
    "state": {
      "positionState": "FORMED"
    }
  },
  "error": null
}
```

## Performance Characteristics

- **API Response Time**: < 50ms (202 Accepted returned immediately)
- **Processing Latency**: P95 < 2 seconds (end-to-end processing)
- **Throughput**: 10-20 trades/sec per instance (sustained)
- **Burst Capacity**: 100-150 trades/sec (with optimizations)
- **Parallel Processing**: Multiple partitions process concurrently
- **Webhook Retry**: 3 attempts with exponential backoff (1s, 2s)
- **Webhook Timeout**: 30 seconds (configurable)
- **Connection Pool**: 50 connections (configurable)
- **Lock Timeout**: 30 seconds (configurable)

## Notes

- All API requests return **202 Accepted** immediately with a `jobId`
- Clients receive completion notifications via **webhook callbacks** to `X-Callback-Url`
- Clients can also **poll** the `/api/v1/trades/jobs/{jobId}/status` endpoint as fallback
- Webhook failures are retried but don't fail the job (job status still updated)
- All external service calls use circuit breakers, retries, and time limiters
- Deadlock retry uses `REQUIRES_NEW` propagation to isolate retries
- Idempotency records have TTL (24 hours in DB, 12 hours in Redis)
- Partition locks are automatically released after processing
- Failed messages are published to DLQ for manual review
- Mock services can be enabled via configuration for testing
- Solace partition routing enables automatic ordering per partition key

## Related Documentation

- [Callback URL Usage Guide](./callback-url-usage-guide.md)
- [API Documentation](./api/trade-capture-service-openapi.yaml)
- [Trade Capture Service Design](./trade-capture-service_design.md)
- [Solace Integration](./solace-partition-routing-implementation.md)
