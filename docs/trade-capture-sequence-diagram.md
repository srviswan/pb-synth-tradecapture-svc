# Trade Capture Service - End-to-End Sequence Diagram

## Overview

This document provides a comprehensive sequence diagram showing the complete end-to-end flow of the Trade Capture Service, from initial trade request through enrichment, rules application, approval workflow, and final blotter publication.

## Complete Flow Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
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
    participant KP as KafkaPublisher

    Note over Client,KP: Trade Capture Request Flow

    Client->>API: POST /api/v1/trades/capture<br/>(TradeCaptureRequest)
    API->>TCS: processTrade(request)
    
    Note over TCS,Redis: Step 1: Partition Locking
    TCS->>PLS: acquireLock(partitionKey)
    PLS->>Redis: SETNX lock:partition:{key}
    Redis-->>PLS: Lock acquired
    PLS-->>TCS: Lock acquired
    
    Note over TCS,DB: Step 2: Idempotency Check
    TCS->>IS: checkDuplicate(request)
    IS->>Redis: GET idempotency:{key}
    alt Cache Hit
        Redis-->>IS: Cached result
        IS-->>TCS: Return cached SwapBlotter
        TCS->>PLS: releaseLock(partitionKey)
        TCS-->>API: DUPLICATE response
        API-->>Client: 200 OK (cached)
    else Cache Miss
        Redis-->>IS: Not found
        IS->>DB: SELECT idempotency_record<br/>WHERE idempotency_key = ?
        DB-->>IS: Record (if exists)
        alt Duplicate Found
            IS-->>TCS: Return cached SwapBlotter
            TCS->>PLS: releaseLock(partitionKey)
            TCS-->>API: DUPLICATE response
            API-->>Client: 200 OK (cached)
        else New Trade
            IS-->>TCS: Not duplicate
        end
    end
    
    Note over TCS,DB: Step 3: Create Idempotency Record
    TCS->>IS: createIdempotencyRecord(request)
    IS->>DB: INSERT idempotency_record<br/>(status: PROCESSING)
    DB-->>IS: Record created
    IS-->>TCS: Record created
    
    Note over TCS,ASC: Step 4: Parallel Enrichment
    par Parallel Enrichment
        TCS->>ES: enrich(request)
        ES->>SMS: lookupSecurityAsync(securityId)
        SMS->>SMS: Mock/Real Service Call
        SMS-->>ES: Security data
    and
        ES->>ASC: lookupAccountAsync(accountId, bookId)
        ASC->>ASC: Mock/Real Service Call
        ASC-->>ES: Account data
    end
    ES-->>TCS: EnrichmentResult<br/>(status, enrichedData)
    
    Note over TCS,RE: Step 5: Build Initial SwapBlotter
    TCS->>TCS: buildInitialSwapBlotter(request, enrichmentResult)
    TCS->>TCS: SwapBlotter created<br/>(enrichmentStatus, workflowStatus: PENDING_APPROVAL)
    
    Note over TCS,RE: Step 6: Apply Rules
    TCS->>RE: applyRules(swapBlotter, tradeData)
    RE->>RE: Evaluate Economic Rules
    RE->>RE: Evaluate Non-Economic Rules
    RE->>RE: Evaluate Workflow Rules
    RE->>TCS: Updated SwapBlotter<br/>(rulesApplied tracked)
    
    Note over TCS,VS: Step 7: Validation
    TCS->>VS: validate(request)
    VS->>VS: Validate ISIN, Account Status, etc.
    VS-->>TCS: Validation passed
    
    Note over TCS,AWS: Step 8: Approval Workflow
    alt Workflow Status = PENDING_APPROVAL
        TCS->>AWS: submitForApproval(swapBlotter)
        AWS->>AWS: Mock/Real Approval Service
        AWS-->>TCS: WorkflowStatus (APPROVED/REJECTED/PENDING)
        
        alt Rejected
            TCS->>IS: markFailed(idempotencyKey)
            TCS->>PLS: releaseLock(partitionKey)
            TCS-->>API: REJECTED response
            API-->>Client: 200 OK (rejected)
        else Pending
            TCS->>PLS: releaseLock(partitionKey)
            TCS-->>API: PENDING_APPROVAL response
            API-->>Client: 200 OK (pending)
        else Approved
            Note over TCS: Continue to Step 9
        end
    end
    
    Note over TCS,DB: Step 9: State Management
    TCS->>SMSvc: updateState(partitionKey, newState)
    SMSvc->>DB: SELECT partition_state<br/>WHERE partition_key = ?<br/>(PESSIMISTIC_WRITE lock)
    DB-->>SMSvc: Current state
    SMSvc->>SMSvc: Validate state transition
    SMSvc->>DB: UPDATE partition_state<br/>(positionState, stateJson, version++)
    DB-->>SMSvc: State updated
    SMSvc-->>TCS: State updated
    
    Note over TCS,DB: Step 10: Save SwapBlotter
    TCS->>SBS: saveSwapBlotter(swapBlotter)
    SBS->>DB: INSERT swap_blotter<br/>(tradeId, partitionKey, swapBlotterJson)
    DB-->>SBS: Blotter saved
    SBS-->>TCS: SwapBlotterEntity
    
    Note over TCS,DB: Step 11: Update Idempotency Record
    TCS->>IS: markCompleted(idempotencyKey, swapBlotterId)
    IS->>DB: UPDATE idempotency_record<br/>(status: COMPLETED, swapBlotterId)
    DB-->>IS: Record updated
    IS->>Redis: SET idempotency:{key} = COMPLETED<br/>(TTL: 12 hours)
    Redis-->>IS: Cached
    IS-->>TCS: Marked completed
    
    Note over TCS,KP: Step 12: Publish to Kafka
    TCS->>KP: publish(swapBlotter)
    KP->>KP: Convert SwapBlotter to Protobuf
    KP->>KP: Publish to topic: trade-capture-blotter
    KP-->>TCS: Published
    
    Note over TCS,Redis: Step 13: Release Lock
    TCS->>PLS: releaseLock(partitionKey)
    PLS->>Redis: DEL lock:partition:{key}
    Redis-->>PLS: Lock released
    PLS-->>TCS: Lock released
    
    Note over TCS,Client: Step 14: Return Response
    TCS-->>API: TradeCaptureResponse<br/>(status: SUCCESS, swapBlotter)
    API-->>Client: 200 OK<br/>(TradeCaptureResponse)
    
    Note over Client,KP: End of Flow
```

## Error Handling Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant IS as IdempotencyService
    participant DB as SQL Server
    participant DLQ as DeadLetterQueue

    Note over Client,DLQ: Error Handling Scenarios

    alt Lock Acquisition Failed
        Client->>API: POST /trades/capture
        API->>TCS: processTrade(request)
        TCS->>PLS: acquireLock(partitionKey)
        PLS-->>TCS: Lock acquisition failed (timeout)
        TCS-->>API: FAILED (LOCK_ACQUISITION_FAILED)
        API-->>Client: 500 Internal Server Error
    end
    
    alt Deadlock During Processing
        TCS->>DB: UPDATE partition_state
        DB-->>TCS: Deadlock error (1205)
        TCS->>TCS: DeadlockRetryAspect intercepts
        TCS->>TCS: Retry with REQUIRES_NEW transaction
        alt Retry Successful
            DB-->>TCS: Update successful
            TCS-->>API: Continue processing
        else Retry Exhausted
            TCS->>IS: markFailed(idempotencyKey)
            TCS-->>API: FAILED (PROCESSING_ERROR)
            API-->>Client: 500 Internal Server Error
        end
    end
    
    alt External Service Failure
        TCS->>SMS: lookupSecurityAsync(securityId)
        SMS-->>TCS: Circuit breaker open / Timeout
        SMS-->>TCS: Fallback (empty Optional)
        TCS->>TCS: Continue with partial enrichment
        TCS-->>API: SUCCESS (enrichmentStatus: PARTIAL)
        API-->>Client: 200 OK (with warning)
    end
    
    alt Validation Failure
        TCS->>VS: validate(request)
        VS-->>TCS: ValidationException
        TCS->>IS: markFailed(idempotencyKey)
        TCS-->>API: FAILED (VALIDATION_ERROR)
        API-->>Client: 400 Bad Request
    end
    
    alt Publishing Failure
        TCS->>KP: publish(swapBlotter)
        KP-->>TCS: Publishing failed
        TCS->>DLQ: publishToDLQ(swapBlotter, error)
        DLQ-->>TCS: Message sent to DLQ
        TCS-->>API: SUCCESS (with DLQ notification)
        API-->>Client: 200 OK (with warning)
    end
```

## Parallel Processing Flow

```mermaid
sequenceDiagram
    participant MQ as Message Queue<br/>(Kafka/Solace)
    participant Consumer as KafkaTradeMessageConsumer
    participant TMP as TradeMessageProcessor
    participant TCS as TradeCaptureService
    participant PLS as PartitionLockService
    participant Redis

    Note over MQ,Redis: Parallel Partition Processing

    par Partition A Processing
        MQ->>Consumer: Message (Partition A)
        Consumer->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-001_BOOK-001_SEC-001")
        PLS->>Redis: Lock for Partition A
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-001_BOOK-001_SEC-001")
    and Partition B Processing
        MQ->>Consumer: Message (Partition B)
        Consumer->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-002_BOOK-002_SEC-002")
        PLS->>Redis: Lock for Partition B
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-002_BOOK-002_SEC-002")
    and Partition C Processing
        MQ->>Consumer: Message (Partition C)
        Consumer->>TMP: processMessage(message)
        TMP->>TCS: processTrade(request)
        TCS->>PLS: acquireLock("ACC-003_BOOK-003_SEC-003")
        PLS->>Redis: Lock for Partition C
        Redis-->>PLS: Lock acquired
        TCS->>TCS: Process trade...
        TCS->>PLS: releaseLock("ACC-003_BOOK-003_SEC-003")
    end
    
    Note over MQ,Redis: All partitions process in parallel<br/>Same partition processes sequentially
```

## Idempotency Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant TCS as TradeCaptureService
    participant IS as IdempotencyService
    participant Redis
    participant DB as SQL Server

    Note over Client,DB: Idempotency Check Flow

    Client->>API: POST /trades/capture<br/>(Idempotency-Key: KEY-123)
    API->>TCS: processTrade(request)
    
    TCS->>IS: checkDuplicate(request)
    
    Note over IS,Redis: L1 Cache Check (Redis)
    IS->>Redis: GET idempotency:KEY-123
    alt Cache Hit - Completed
        Redis-->>IS: COMPLETED
        IS->>IS: Get swapBlotterId from cache
        IS->>DB: SELECT swap_blotter<br/>WHERE trade_id = ?
        DB-->>IS: SwapBlotter
        IS-->>TCS: Return cached SwapBlotter
        TCS-->>API: DUPLICATE response
        API-->>Client: 200 OK (cached)
    else Cache Hit - Processing
        Redis-->>IS: PROCESSING
        IS-->>TCS: Trade still processing
        TCS-->>API: PENDING response
        API-->>Client: 200 OK (pending)
    else Cache Miss
        Redis-->>IS: Not found
        
        Note over IS,DB: L2 Cache Check (Database)
        IS->>DB: SELECT idempotency_record<br/>WHERE idempotency_key = ?<br/>AND archive_flag = 0
        alt Record Found - Completed
            DB-->>IS: Record (status: COMPLETED)
            IS->>Redis: SET idempotency:KEY-123 = COMPLETED<br/>(TTL: 12 hours)
            IS->>DB: SELECT swap_blotter<br/>WHERE trade_id = ?
            DB-->>IS: SwapBlotter
            IS-->>TCS: Return cached SwapBlotter
            TCS-->>API: DUPLICATE response
            API-->>Client: 200 OK (cached)
        else Record Found - Processing
            DB-->>IS: Record (status: PROCESSING)
            IS->>Redis: SET idempotency:KEY-123 = PROCESSING<br/>(TTL: 12 hours)
            IS-->>TCS: Trade still processing
            TCS-->>API: PENDING response
            API-->>Client: 200 OK (pending)
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

## Approval Workflow Sequence

```mermaid
sequenceDiagram
    participant Client
    participant API as TradeCaptureController
    participant TCS as TradeCaptureService
    participant RE as RulesEngine
    participant AWS as ApprovalWorkflowServiceClient
    participant ApprovalSvc as Approval Workflow Service
    participant SBS as SwapBlotterService
    participant KP as KafkaPublisher

    Note over Client,KP: Approval Workflow Flow

    Client->>API: POST /trades/capture<br/>(source: MANUAL)
    API->>TCS: processTrade(request)
    
    Note over TCS,RE: Rules Application
    TCS->>RE: applyRules(swapBlotter, tradeData)
    RE->>RE: Evaluate workflow rules
    RE-->>TCS: SwapBlotter<br/>(workflowStatus: PENDING_APPROVAL)
    
    alt Workflow Status = PENDING_APPROVAL
        TCS->>AWS: submitForApproval(swapBlotter)
        
        alt Mock Mode
            AWS->>AWS: Auto-approve (mock)
            AWS-->>TCS: WorkflowStatus.APPROVED
        else Real Service
            AWS->>ApprovalSvc: POST /api/v1/approvals/submit
            ApprovalSvc-->>AWS: Approval response
            AWS-->>TCS: WorkflowStatus (APPROVED/REJECTED/PENDING)
        end
        
        alt Approved
            TCS->>TCS: Set workflowStatus = APPROVED
            TCS->>SBS: saveSwapBlotter(swapBlotter)
            TCS->>KP: publish(swapBlotter)
            TCS-->>API: SUCCESS response
            API-->>Client: 200 OK (approved)
        else Rejected
            TCS->>TCS: Mark idempotency as failed
            TCS-->>API: REJECTED response
            API-->>Client: 200 OK (rejected)
        else Pending
            TCS-->>API: PENDING_APPROVAL response
            API-->>Client: 200 OK (pending)
            Note over Client: Client can poll for status
        end
    else Workflow Status = APPROVED (auto-approved)
        TCS->>SBS: saveSwapBlotter(swapBlotter)
        TCS->>KP: publish(swapBlotter)
        TCS-->>API: SUCCESS response
        API-->>Client: 200 OK
    end
```

## Components Legend

### Services
- **TradeCaptureController**: REST API endpoint handler
- **TradeCaptureService**: Main orchestration service
- **PartitionLockService**: Distributed locking using Redis
- **IdempotencyService**: Duplicate detection and caching
- **EnrichmentService**: Parallel enrichment orchestration
- **SecurityMasterServiceClient**: Security data lookup (with circuit breaker)
- **AccountServiceClient**: Account/Book data lookup (with circuit breaker)
- **RulesEngine**: Rule evaluation and application
- **ValidationService**: Trade validation
- **ApprovalWorkflowServiceClient**: Approval workflow integration
- **StateManagementService**: CDM-compliant state management
- **SwapBlotterService**: Blotter persistence
- **KafkaPublisher**: Message publishing to Kafka

### Infrastructure
- **Redis**: Distributed locking and idempotency cache (L1)
- **SQL Server**: Persistent storage (idempotency records, swap blotter, partition state)
- **Kafka**: Message queue for blotter publishing
- **DeadLetterQueue**: Failed message handling

## Key Design Patterns

1. **Partition-Based Processing**: Trades partitioned by `{accountId}_{bookId}_{securityId}`
2. **Distributed Locking**: Redis-based locks ensure sequential processing per partition
3. **Two-Level Caching**: Redis (L1) + Database (L2) for idempotency checks
4. **Parallel Enrichment**: Concurrent calls to SecurityMaster and AccountService
5. **Circuit Breaker Pattern**: Resilience4j for external service calls
6. **Deadlock Retry**: Automatic retry with `REQUIRES_NEW` transactions
7. **Idempotency**: Prevents duplicate processing via idempotency keys
8. **Approval Workflow**: Conditional approval for manual trades
9. **State Management**: CDM-compliant state transitions with optimistic locking

## Performance Characteristics

- **Latency**: P95 < 50ms (under normal load)
- **Throughput**: 10-20 trades/sec per instance (sustained)
- **Burst Capacity**: 100-150 trades/sec (with optimizations)
- **Parallel Processing**: Multiple partitions process concurrently
- **Connection Pool**: 50 connections (configurable)
- **Lock Timeout**: 30 seconds (configurable)

## Notes

- All external service calls use circuit breakers, retries, and time limiters
- Deadlock retry uses `REQUIRES_NEW` propagation to isolate retries
- Idempotency records have TTL (24 hours in DB, 12 hours in Redis)
- Partition locks are automatically released after processing
- Failed messages are published to DLQ for manual review
- Mock services can be enabled via configuration for testing

