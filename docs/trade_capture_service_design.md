# PB Synthetic Trade Capture Service Design

## Overview

The pb-synth-tradecapture-svc (PB Synthetic Trade Capture Service) processes incoming trade lots from upstream systems (front office trading and allocation management), applies economic and non-economic rules, performs validations and enrichments, and produces SwapBlotter output containing fully enriched contracts with EconomicTerms, PerformancePayout, and InterestPayout details.

### Key Features

- **Dual Processing Modes**: Real-time streaming via messaging queues and REST API
- **Multi-Subscriber Publishing**: SwapBlotter published to multiple downstream services via queues and/or APIs
- **Flexible Publishing**: Supports async messaging (Solace, Kafka, RabbitMQ, etc.) and API-based delivery (webhooks, REST, gRPC)
- **Partition-Aware Sequencing**: Maintains sequence per Account/Book + Security combination
- **Rule-Based Processing**: Configurable Economic, Non-Economic, and Workflow rules
- **CDM-Compliant State Management**: Follows CDM PositionStatusEnum state transitions
- **High Scalability**: Designed for 2M trades/day with burst capacity during market hours
- **Manual Trade Entry**: Supports manual trade entry screen integration

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    API Layer (REST)                          │
│  TradeCaptureController, ManualTradeEntryController          │
│  OpenAPI: trade-capture-service-openapi.yaml                │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              Service Layer (Business Logic)                  │
│  TradeCaptureService (orchestration)                        │
│  RulesEngine (Economic, Non-Economic, Workflow)             │
│  EnrichmentService, ValidationService                       │
│  StateManagementService (CDM-compliant)                    │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│    Real-Time Processing (Messaging Queues)                  │
│  MessageListener (protobuf deserialization)                │
│  PartitionProcessor (partition-aware sequencing)           │
│  Multi-Publisher (protobuf/JSON serialization)             │
│  - Queue Publishers (Solace, Kafka, RabbitMQ, etc.)        │
│  - API Publishers (Webhooks, REST, gRPC)                    │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│         Data Provider Layer (Abstraction)                   │
│  SecurityMasterService (Product/Security lookup)           │
│  AccountService (Book/Account lookup)                      │
│  StateRepository (partition state management)               │
│  RulesRepository (in-memory cache of active rules)         │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│          Integration Layer (External Services)               │
│  Messaging Systems (Solace, Kafka, RabbitMQ, etc.)        │
│  Downstream Services (via queues, webhooks, REST, gRPC)    │
│  Reference Data Services (Security, Account)               │
│  Rule Management Service (API integration for rules)        │
│  Approval Workflow Service (for manual approvals)          │
└─────────────────────────────────────────────────────────────┘
```

### Processing Flow

```
1. Trade Input (API or Solace Queue)
   │
   ├─ Extract idempotency key (tradeId or idempotencyKey)
   │
   ├─ Check idempotency (cache + database)
   │   ├─ If duplicate: Return cached result or reject
   │   └─ If new: Continue processing
   │
   ├─ Extract partition key (Account/Book + Security)
   │
   ├─ Acquire partition lock (distributed lock)
   │
   ├─ Re-check idempotency within lock (double-check)
   │
   ├─ Insert idempotency record (status: PROCESSING)
   │
   ├─ Load current state for partition (if exists)
   │
2. Enrichment Phase
   │
   ├─ Lookup Security/Product (SecurityMasterService)
   │
   ├─ Lookup Account/Book (AccountService)
   │
   ├─ Populate missing Product details
   │
   └─ Populate missing Party details
   │
3. Rules Application Phase
   │
   ├─ Apply Economic Rules
   │   └─ Create EconomicTerms, PerformancePayout, InterestPayout
   │
   ├─ Apply Non-Economic Rules
   │   └─ Set legal entities, documentation, regulatory flags
   │
   └─ Apply Workflow Rules
       └─ Determine WorkflowStatus (APPROVED, PENDING_APPROVAL, REJECTED)
   │
4. Validation Phase
   │
   ├─ Validate ISINs, open books, credit limits
   │
   └─ Validate state transitions
   │
5. State Management Phase
   │
   ├─ Validate CDM state transition
   │
   └─ Update state (CDM PositionStatusEnum)
   │
6. Output Generation
   │
   ├─ Construct SwapBlotter
   │
   ├─ Serialize to protobuf (if from queue)
   │
   └─ Publish SwapBlotter (multiple subscribers)
       ├─ Async Messaging Queues (pub/sub pattern)
       │   ├─ Solace PubSub+ (trade/capture/blotter)
       │   ├─ Kafka (if configured)
       │   ├─ RabbitMQ (if configured)
       │   └─ Other messaging systems (configurable)
       │
       └─ API-Based Publishing (webhooks, REST)
           ├─ Webhook callbacks (HTTP POST)
           ├─ REST API push (HTTP POST)
           └─ gRPC streaming (if configured)
   │
7. Update idempotency record (status: COMPLETED, store SwapBlotter reference)
   │
8. Release partition lock
```

## Data Models

### TradeCaptureRequest (Input)

Represents incoming trade lots from upstream systems.

**Key Fields:**
- `tradeId`: Unique trade identifier (required for idempotency)
- `idempotencyKey`: Optional idempotency key for duplicate detection (if not provided, tradeId is used)
- `accountId`: Account identifier (part of partition key)
- `bookId`: Book identifier (part of partition key)
- `securityId`: Security identifier (part of partition key)
- `source`: Trade source (AUTOMATED or MANUAL)
- `tradeLots`: List of trade lots (CDM TradeLot)
- `tradeDate`: Trade date
- `counterpartyIds`: List of counterparty identifiers
- `metadata`: Additional metadata

**Partition Key**: `{accountId}_{bookId}_{securityId}`

**Idempotency Key**: 
- Primary: `tradeId` (always unique per trade)
- Secondary: `idempotencyKey` (optional, for client-provided idempotency)
- Composite: `{tradeId}_{source}_{tradeDate}` (for additional uniqueness)

### SwapBlotter (Output)

Java POJO inspired by CDM TradeState, containing fully enriched contract.

**Key Fields:**
- `tradeId`: Unique trade identifier
- `partitionKey`: Partition key (Account/Book + Security)
- `tradeLots`: List of trade lots (CDM TradeLot)
- `contract`: Contract (NonTransferableProduct wrapper)
  - `identifier`: Product identifiers
  - `taxonomy`: Product taxonomy
  - `economicTerms`: Economic terms
    - `payout`: List of payouts (PerformancePayout, InterestPayout)
- `state`: State (CDM State with PositionStatusEnum)
- `enrichmentStatus`: Enrichment status (COMPLETE, PARTIAL, FAILED, PENDING)
- `workflowStatus`: Workflow status (APPROVED, PENDING_APPROVAL, REJECTED)
- `processingMetadata`: Processing metadata (rules applied, processing time, etc.)

### Protobuf Message Format

**TradeCaptureMessage** (Input):
- Serialized in protobuf format
- Published to Solace queue: `trade/capture/input`
- Contains: trade data, partition key, sequence number, source type

**SwapBlotterMessage** (Output):
- Serialized in protobuf format (for messaging queues)
- Can be published to multiple messaging systems simultaneously
- Contains: enriched SwapBlotter with contract, state, workflow status
- Supports multiple subscribers/consumers via pub/sub pattern

**SwapBlotter Publishing:**
- **Async Messaging**: Published to configured messaging queues (Solace, Kafka, RabbitMQ, etc.)
- **API-Based**: Can be delivered via webhooks, REST API calls, or gRPC streaming
- **Multiple Subscribers**: Each SwapBlotter can be consumed by multiple downstream services
- **Delivery Guarantees**: At-least-once delivery for messaging, best-effort for API calls

See: `trade_capture_message.proto` for complete schema definition.

## Idempotency and Duplicate Prevention

### Idempotency Strategy

The service implements multiple layers of idempotency to prevent duplicate trade processing:

#### 1. Trade ID Uniqueness

**Primary Idempotency Key**: `tradeId`
- Each trade must have a unique `tradeId` across the system
- Database-level unique constraint on `tradeId` (or composite key with source)
- Reject duplicate `tradeId` submissions within idempotency window

**Idempotency Window**:
- Default: 24 hours (configurable)
- Trades with same `tradeId` submitted within window are treated as duplicates
- After window expires, same `tradeId` can be resubmitted (for corrections/amendments)

#### 2. Database-Level Deduplication

**Idempotency Table**:
- Stores processed trade identifiers with timestamps
- Unique constraint: `(tradeId, source, tradeDate)` or `(idempotencyKey)`
- TTL-based cleanup: Remove entries older than idempotency window
- Fast lookup: Indexed for O(1) duplicate detection

**Processing Flow**:
1. Extract idempotency key from request (`tradeId` or `idempotencyKey`)
2. Check idempotency table for existing entry
3. If exists:
   - Return cached SwapBlotter (if processing completed)
   - Return processing status (if still processing)
   - Reject with duplicate error (if within window and different payload)
4. If not exists:
   - Insert idempotency record with status "PROCESSING"
   - Process trade
   - Update status to "COMPLETED" or "FAILED"
   - Store SwapBlotter reference

#### 3. Message-Level Deduplication

**Message Deduplication** (for queue-based processing):
- Message brokers (Solace, Kafka) may deliver messages multiple times
- Service maintains message ID tracking per partition
- Processed message IDs stored in Redis with TTL
- Skip processing if message ID already seen

**Sequence Number Validation**:
- Protobuf messages include sequence numbers
- Validate sequence number is greater than last processed for partition
- Reject out-of-order or duplicate sequence numbers
- Handle sequence gaps (missing messages)

#### 4. Partition-Level Idempotency

**Partition Lock + Idempotency**:
- Acquire partition lock before processing
- Check idempotency within lock scope
- Prevents race conditions in concurrent processing
- Ensures only one instance processes same trade

**Distributed Locking**:
- Redis-based distributed locks for partition-level coordination
- Lock timeout prevents deadlocks
- Lock renewal for long-running processing

#### 5. REST API Idempotency

**Idempotency Key Header**:
- Clients can provide `Idempotency-Key` HTTP header
- Service uses header value for duplicate detection
- If not provided, uses `tradeId` from request body
- Standard header: `Idempotency-Key: <unique-key>`

**Idempotency Response**:
- First request: Process and return 201 Created with SwapBlotter
- Duplicate request: Return 200 OK with cached SwapBlotter
- Include `Idempotency-Status` header: `new` or `duplicate`

**Idempotency Key Format**:
- UUID recommended
- Client-generated, unique per trade submission
- Persisted for idempotency window duration

#### 6. Idempotency Storage

**Storage Strategy**:
- **In-Memory Cache (Redis)**: Fast lookup for recent trades (TTL: 1 hour)
- **Database Table**: Persistent storage for idempotency window (TTL: 24 hours)
- **Two-Phase Lookup**: Check cache first, then database

**Idempotency Record Structure**:
```
{
  idempotencyKey: string (tradeId or idempotencyKey)
  tradeId: string
  partitionKey: string
  status: PROCESSING | COMPLETED | FAILED
  swapBlotterId: string (reference to processed result)
  createdAt: timestamp
  completedAt: timestamp
  expiresAt: timestamp (TTL)
}
```

#### 7. Handling Duplicate Scenarios

**Scenario 1: Duplicate Trade ID (Same Payload)**
- Detect duplicate within idempotency window
- Return cached SwapBlotter (if processing completed)
- Return processing status (if still processing)
- No re-processing, no side effects

**Scenario 2: Duplicate Trade ID (Different Payload)**
- Detect duplicate with different trade data
- Reject with error: `DUPLICATE_TRADE_ID_DIFFERENT_PAYLOAD`
- Log warning for investigation
- Require explicit override flag to process

**Scenario 3: Retry After Failure**
- Failed trade can be retried with same `tradeId`
- Check if previous attempt exists
- If previous attempt failed, allow retry
- If previous attempt succeeded, return cached result

**Scenario 4: Message Queue Redelivery**
- Message broker redelivers unacknowledged messages
- Check message ID in processed messages cache
- Skip processing if already processed
- Acknowledge message to prevent further redelivery

#### 8. Idempotency Configuration

**Configuration Properties**:
```yaml
idempotency:
  enabled: true
  window-hours: 24
  cache-ttl-hours: 1
  storage-type: redis+database  # redis, database, or both
  key-strategy: tradeId  # tradeId, idempotencyKey, or composite
  allow-payload-differences: false
  retry-on-failure: true
```

**Idempotency Key Strategy**:
- `tradeId`: Use tradeId as idempotency key (default)
- `idempotencyKey`: Use client-provided idempotency key
- `composite`: Use combination of tradeId, source, and tradeDate

#### 9. Monitoring and Alerting

**Idempotency Metrics**:
- Duplicate detection rate
- Idempotency cache hit rate
- Idempotency storage size
- Duplicate rejection rate
- Payload mismatch rate

**Alerts**:
- High duplicate detection rate (potential issue)
- Idempotency storage growth (cleanup issues)
- Payload mismatches (data integrity concern)

## Partitioning Strategy and Sequencing

### Partition Key

**Format**: `{accountId}_{bookId}_{securityId}`

**Purpose**: Ensures trades for the same Account/Book + Security combination are processed sequentially.

### Sequencing Guarantees

1. **Per-Partition Sequencing**: Trades with the same partition key are processed in order
2. **Parallel Processing**: Different partitions can be processed in parallel
3. **Sequence Numbers**: Protobuf messages include sequence numbers to detect out-of-order processing
4. **Distributed Locking**: Partition locks ensure only one trade per partition is processed at a time

### State Consistency

- **State Snapshot**: Current state stored per partition key
- **Optimistic Locking**: Version numbers on SwapBlotter detect concurrent modifications
- **State Validation**: CDM-compliant state transitions validated before applying

## State Management (CDM-Compliant)

### CDM PositionStatusEnum States

- **Executed**: Trade has been executed (risk transferred)
- **Formed**: Contract has been formed
- **Settled**: Position has settled
- **Cancelled**: Position has been cancelled
- **Closed**: Position has been closed

### Valid State Transitions

```
Executed → Formed → Settled
Executed → Cancelled
Any → Closed
```

### State Validation Rules

- Invalid transitions are rejected (e.g., Closed → Executed)
- State consistency maintained per Account/Book + Security partition
- State transitions validated before applying

## Rules Engine

### Rule Management Architecture

**Economic and Non-Economic Rules**: Received via API integration from external rule management service.
- Rules are pushed/updated dynamically via API endpoints
- Service maintains in-memory cache of active rules for performance
- Rules can be enabled/disabled via API without service restart
- Rule updates take effect immediately (hot reload)
- Rules are validated before being cached

**Workflow Rules**: Can be either:
- API-based (from external rule management service)
- Configuration-based (loaded from local config files at startup)

**Rule API Endpoints:**
- `POST /api/v1/rules/economic` - Add/update economic rule
- `POST /api/v1/rules/non-economic` - Add/update non-economic rule
- `POST /api/v1/rules/workflow` - Add/update workflow rule (if API-based)
- `GET /api/v1/rules` - List all active rules (with optional filtering)
- `GET /api/v1/rules/{ruleId}` - Get specific rule
- `DELETE /api/v1/rules/{ruleId}` - Delete/disable rule
- `POST /api/v1/rules/batch` - Bulk update rules

**Rule Caching:**
- Rules cached in-memory for fast evaluation
- Cache invalidated on rule updates
- Rules sorted by priority for efficient evaluation
- Enabled/disabled status checked during evaluation

### Rule Types

#### 1. Economic Rules

Determine payout schedules, day counts, resetting behaviors based on product attributes.

**Examples:**
- Set day count fraction based on currency (USD → ACT/360, EUR → ACT/365)
- Set fixed rate for retail accounts
- Set performance payout return type (Price vs Total)

**Targets:**
- `ECONOMIC_TERMS`
- `PERFORMANCE_PAYOUT`
- `INTEREST_PAYOUT`
- `PAYOUT_SCHEDULE`
- `DAY_COUNT`
- `RESET_BEHAVIOR`

#### 2. Non-Economic Rules

Logic for legal entities, documentation clauses, and regulatory flags.

**Examples:**
- Set legal entity based on account
- Set regulatory flag for high-risk securities
- Set documentation requirements based on trade amount

**Targets:**
- `LEGAL_ENTITY`
- `DOCUMENTATION`
- `REGULATORY_FLAG`
- `COUNTERPARTY`
- `ACCOUNT`

#### 3. Workflow Rules

Determine if trade is manual vs automated, approval requirements, routing decisions.

**Examples:**
- Require approval for manual trades
- Require approval for retail accounts with high amounts
- Require approval for high-risk counterparties
- Auto-approve low-risk automated trades

**Targets:**
- `WORKFLOW_STATUS`
- `APPROVAL_REQUIRED`
- `ROUTING`

### Rule Evaluation Order

1. **Economic Rules** (first)
2. **Non-Economic Rules** (second)
3. **Workflow Rules** (last, can depend on results from economic/non-economic rules)

### Rule Management

**Economic and Non-Economic Rules**: Received via API integration from external rule management service.
- Rules are pushed/updated dynamically via API
- Service maintains in-memory cache of active rules
- Rules can be enabled/disabled via API
- Rule updates take effect immediately (hot reload)

**Workflow Rules**: Can be either API-based (from external service) or configuration-based (local config files).

**Rule API Integration:**
- `POST /api/v1/rules/economic` - Add/update economic rule
- `POST /api/v1/rules/non-economic` - Add/update non-economic rule
- `POST /api/v1/rules/workflow` - Add/update workflow rule (if API-based)
- `GET /api/v1/rules` - List all active rules
- `GET /api/v1/rules/{ruleId}` - Get specific rule
- `DELETE /api/v1/rules/{ruleId}` - Delete/disable rule
- `POST /api/v1/rules/batch` - Bulk update rules

**Rule Structure:**
- `id`: Unique rule identifier
- `name`: Human-readable name
- `priority`: Rule priority (lower = higher priority)
- `enabled`: Whether rule is enabled
- `criteria`: List of criteria (field, operator, value)
- `actions`: List of actions to execute when criteria are met

**Rule Schema Reference:**
- Schema definition: `rule-configuration-schema.json`
- Example structure: `rules-configuration-example.yaml`

## Workflow Management (Rule-Based)

### Workflow Status Values

- **APPROVED**: Trade approved for STP processing
- **PENDING_APPROVAL**: Trade requires manual approval
- **REJECTED**: Trade rejected (by rules or validation)

### Workflow Determination

Workflow status is determined by evaluating **Workflow Rules**:
- Rules check: source system, account type, trade amount, counterparty risk, credit limits
- Rules have access to enriched trade data, economic terms, validation results
- First matching rule (by priority) determines workflow status

### Approval Process

- If `workflowStatus = PENDING_APPROVAL`:
  - Trigger notification (stub/log)
  - Route to approval workflow service
  - Trade remains in pending state until approved

### STP Flow

- If `workflowStatus = APPROVED`:
  - Trade proceeds to contract creation and enrichment
  - Automated processing continues without manual intervention
  - SwapBlotter published to all configured subscribers (messaging queues and/or API endpoints)

## Scalability and Resilience (Burst-Aware)

### Traffic Pattern

- **Total Volume**: 2M trades/day
- **Peak Windows**: 3-6 PM market hours for AMER, APAC, EMEA regions
- **Peak Capacity**: ~186 trades/sec (all regions) or ~62 trades/sec per region
- **Burst Multiplier**: 8-10x average rate during peak windows

### Horizontal Scaling

- **Stateless Design**: Service instances are stateless
- **Partition Affinity**: Each instance handles specific partitions (consistent hashing)
- **Auto-Scaling**: Scale instances based on queue depth, CPU, request rate
- **Peak Hour Scaling**: Pre-scale or reactive scale during known peak windows

### Queue Buffering

- **Solace Queue**: Acts as buffer during peak loads
- **Async Processing**: Allows async processing of trades
- **Backpressure**: Implement backpressure when downstream services are slow

### Resilience Patterns

- **Circuit Breakers**: Resilience4j for external service calls
- **Retry Logic**: Exponential backoff retry (partition-aware)
- **Dead Letter Queue (DLQ)**: Failed messages go to DLQ for manual review
- **Rate Limiting**: Protect downstream services (per partition, with burst allowance)
- **Monitoring**: Metrics for throughput, latency, error rates, queue depth, partition lag

## API Specifications

### REST API Endpoints

**Trade Capture Endpoints:**
- `POST /api/v1/trades/capture` - Synchronous trade capture
- `POST /api/v1/trades/capture/async` - Asynchronous trade capture
- `POST /api/v1/trades/capture/batch` - Batch trade capture
- `POST /api/v1/trades/manual-entry` - Manual trade entry
- `GET /api/v1/trades/capture/status/{jobId}` - Get async job status
- `GET /api/v1/trades/capture/{tradeId}` - Get SwapBlotter by trade ID

**Rule Management Endpoints:**
- `POST /api/v1/rules/economic` - Add/update economic rule (via API integration)
- `POST /api/v1/rules/non-economic` - Add/update non-economic rule (via API integration)
- `POST /api/v1/rules/workflow` - Add/update workflow rule
- `GET /api/v1/rules` - List all active rules (with filtering)
- `GET /api/v1/rules/{ruleId}` - Get specific rule
- `DELETE /api/v1/rules/{ruleId}` - Delete/disable rule
- `POST /api/v1/rules/batch` - Bulk update rules

**Health Endpoints:**
- `GET /api/v1/health` - Health check

See: `api/trade-capture-service-openapi.yaml` for complete API specification.

### Output Publishing Strategy

The service supports multiple publishing mechanisms to deliver SwapBlotter to downstream consumers:

#### Async Messaging Queues (Pub/Sub Pattern)

**Primary Messaging System**: Solace PubSub+
- **Output Topic/Queue**: `trade/capture/blotter`
- **Protocol**: Protobuf serialization
- **Partition Key**: Same as input (Account/Book + Security)
- **Multiple Subscribers**: Supports multiple consumers subscribing to the same topic
- **Delivery**: At-least-once delivery guarantee
- **Message Format**: SwapBlotterMessage (protobuf)

**Alternative Messaging Systems** (configurable):
- **Apache Kafka**: Topic-based publishing with consumer groups
- **RabbitMQ**: Exchange-based routing with multiple queues
- **AWS SQS/SNS**: Cloud-native messaging (if deployed on AWS)
- **Azure Service Bus**: Cloud messaging (if deployed on Azure)
- **Google Pub/Sub**: Cloud messaging (if deployed on GCP)

**Multi-Queue Publishing**:
- Service can publish to multiple messaging systems simultaneously
- Each messaging system can have multiple subscribers
- Configuration-driven: Enable/disable specific messaging systems via properties
- Independent failure handling: Failure in one system doesn't block others

#### API-Based Publishing

**Webhook Callbacks**:
- HTTP POST to configured webhook URLs
- Supports multiple webhook endpoints
- Retry logic with exponential backoff
- Configurable timeout and retry limits
- Payload: JSON or protobuf (base64 encoded)

**REST API Push**:
- HTTP POST to downstream service REST endpoints
- Supports multiple downstream services
- Circuit breaker pattern for resilience
- Rate limiting per downstream service
- Payload: JSON format

**gRPC Streaming** (if configured):
- Bidirectional streaming for real-time delivery
- Supports multiple gRPC clients
- Protobuf payload for efficiency
- Connection pooling and load balancing

#### Publishing Configuration

**Subscriber Management**:
- Configuration file or database-driven subscriber registry
- Each subscriber specifies:
  - Delivery method (queue, webhook, REST API, gRPC)
  - Endpoint/topic details
  - Retry policy
  - Filtering criteria (optional)
- Dynamic subscriber registration (via API)

**Publishing Flow**:
1. SwapBlotter generated after processing
2. Publisher service identifies active subscribers
3. For each subscriber:
   - Serialize SwapBlotter in appropriate format
   - Publish to configured delivery mechanism
   - Handle failures independently
4. Track delivery status per subscriber
5. Retry failed deliveries based on subscriber policy

**Failure Handling**:
- **Messaging Queues**: DLQ for failed messages, manual retry
- **Webhooks/API**: Retry with exponential backoff, dead letter handling
- **Partial Failures**: Continue publishing to other subscribers even if one fails
- **Monitoring**: Track delivery success/failure rates per subscriber

### Input Queue Integration

**Input Queue**: `trade/capture/input`
- Consumes protobuf messages
- Partition-aware processing
- Single-threaded per partition
- Primary source: Solace PubSub+ (configurable to other messaging systems)

## Manual Trade Entry Screen Integration

### Flow

1. User enters trade via web UI
2. Form validation (client-side and server-side)
3. `POST /api/v1/trades/manual-entry` endpoint called
4. Trade validated and serialized to protobuf
5. Published to Solace queue (`trade/capture/input`)
6. Marked with `source = MANUAL`
7. Returns confirmation/error to UI

### Workflow Rules

Manual trades are evaluated by workflow rules:
- Default: `PENDING_APPROVAL` (via `WORKFLOW_RULE_001`)
- Can be overridden by other workflow rules based on account type, amount, etc.

## Database Architecture

### MS SQL Server Configuration

The service uses **MS SQL Server (latest version)** as the primary database with the following features:

**Table Partitioning:**
- All tables are partitioned by date (created_at column)
- Partition function: `PF_TradeCapture_DateRange` with 6-month intervals
- Partition scheme: `PS_TradeCapture_DateRange`
- Enables efficient querying and maintenance of large datasets
- Supports partition elimination for better query performance

**Archive Support:**
- All tables include `archive_flag` column (BIT, default 0)
- Archived records are marked but not deleted (soft delete pattern)
- Filtered indexes exclude archived records for active queries
- Archive operations can be performed:
  - By trade ID (archive specific trade)
  - By date range (bulk archiving)
  - For expired idempotency records (automated cleanup)

**Archive Benefits:**
- Historical data retention without performance impact
- Ability to restore archived records if needed
- Efficient querying (filtered indexes on non-archived records)
- Compliance with data retention policies
- Partition-level archiving for efficient maintenance

**Archive Operations:**
- `POST /api/v1/archive/trades/{tradeId}` - Archive specific trade
- `POST /api/v1/archive/date-range` - Archive by date range
- `POST /api/v1/archive/expired-idempotency` - Archive expired idempotency records

**Stored Procedures:**
- `sp_ArchiveSwapBlotter` - Archive trade and related records
- `sp_ArchiveByDateRange` - Bulk archive by date range
- `sp_ArchiveExpiredIdempotencyRecords` - Archive expired records

**Partition Management:**
- Partitions can be added/removed as needed
- Old partitions can be moved to separate filegroups for archival storage
- Partition switching for efficient data movement

## Database Architecture

### MS SQL Server Configuration

The service uses **MS SQL Server (latest version)** as the primary database with the following features:

**Table Partitioning:**
- All tables are partitioned by date (created_at column)
- Partition function: `PF_TradeCapture_DateRange` with 6-month intervals
- Partition scheme: `PS_TradeCapture_DateRange`
- Enables efficient querying and maintenance of large datasets
- Supports partition elimination for better query performance

**Archive Support:**
- All tables include `archive_flag` column (BIT, default 0)
- Archived records are marked but not deleted (soft delete pattern)
- Filtered indexes exclude archived records for active queries
- Archive operations can be performed:
  - By trade ID (archive specific trade)
  - By date range (bulk archiving)
  - For expired idempotency records (automated cleanup)

**Archive Benefits:**
- Historical data retention without performance impact
- Ability to restore archived records if needed
- Efficient querying (filtered indexes on non-archived records)
- Compliance with data retention policies
- Partition-level archiving for efficient maintenance

**Archive Operations:**
- `POST /api/v1/archive/trades/{tradeId}` - Archive specific trade
- `POST /api/v1/archive/date-range` - Archive by date range
- `POST /api/v1/archive/expired-idempotency` - Archive expired idempotency records

**Stored Procedures:**
- `sp_ArchiveSwapBlotter` - Archive trade and related records
- `sp_ArchiveByDateRange` - Bulk archive by date range
- `sp_ArchiveExpiredIdempotencyRecords` - Archive expired records

**Partition Management:**
- Partitions can be added/removed as needed
- Old partitions can be moved to separate filegroups for archival storage
- Partition switching for efficient data movement

## Performance Optimizations

### Caching Strategy

- **L1 Cache**: In-memory cache for reference data
- **L2 Cache**: Redis cache for reference data and state
- **Partition-Level Caching**: Cache state snapshots per partition
- **Pre-warming**: Pre-warm caches before peak hours

### Connection Pooling

- Efficient database/external service connection management
- Size pools for peak capacity
- Async/non-blocking database access where applicable

### Batch Processing

- Process multiple trades in parallel (within same partition)
- Group trades by partition for efficient processing

### Protobuf Efficiency

- Efficient serialization (smaller payload, faster parsing)
- Used for all queue messages

## Monitoring and Observability

### Key Metrics

- **Throughput**: Trades processed per second
- **Latency**: Processing time per trade (P50, P95, P99)
- **Error Rates**: Error rate by error type
- **Queue Depth**: Messages waiting in queue
- **Partition Lag**: Lag per partition
- **Burst Detection**: Detection of peak load periods

### Health Checks

- Database connectivity
- Solace queue connectivity
- External service availability (enrichment, validation)
- Service uptime and version

## Error Handling

### Error Types

- **VALIDATION_ERROR**: Business rule validation failed
- **ENRICHMENT_ERROR**: Failed to enrich trade data
- **STATE_ERROR**: Invalid state transition
- **RULES_ERROR**: Rule evaluation error
- **SERVICE_UNAVAILABLE**: External service unavailable
- **DUPLICATE_TRADE_ID**: Trade with same tradeId already processed (idempotency)
- **DUPLICATE_TRADE_ID_DIFFERENT_PAYLOAD**: Trade with same tradeId but different payload
- **IDEMPOTENCY_ERROR**: Idempotency check failed

### Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Trade validation failed",
    "details": {
      "field": "securityId",
      "reason": "Security not found in reference data"
    },
    "timestamp": "2024-01-31T10:30:00Z",
    "path": "/api/v1/trades/capture"
  }
}
```

### Duplicate Trade Error Response

```json
{
  "error": {
    "code": "DUPLICATE_TRADE_ID",
    "message": "Trade with tradeId 'TRADE-2024-001' already processed",
    "details": {
      "tradeId": "TRADE-2024-001",
      "originalProcessedAt": "2024-01-31T10:30:00Z",
      "swapBlotterId": "BLOTTER-2024-001"
    },
    "timestamp": "2024-01-31T10:35:00Z",
    "path": "/api/v1/trades/capture"
  }
}
```

**Idempotency Response (Duplicate Request)**:
```json
{
  "tradeId": "TRADE-2024-001",
  "status": "DUPLICATE",
  "swapBlotter": {
    // Cached SwapBlotter from original processing
  },
  "originalProcessedAt": "2024-01-31T10:30:00Z"
}
```

## Containerization and Deployment

### Docker Container Support

The service is designed to run in Docker containers, enabling:
- **Consistent deployment** across development, testing, and production environments
- **Horizontal scaling** through container orchestration (Kubernetes, Docker Swarm)
- **Isolation** of dependencies and runtime environment
- **Portability** across different infrastructure platforms

### Container Requirements

**Base Image Considerations:**
- Use Java 21 runtime base image (e.g., `eclipse-temurin:21-jre-alpine` or `openjdk:21-jre-slim`)
- Minimal base image to reduce attack surface and image size
- Multi-stage builds to optimize final image size

**Container Configuration:**
- Expose port 8080 for REST API (configurable via environment variable)
- Health check endpoint: `GET /api/v1/health`
- Graceful shutdown support for in-flight message processing
- Resource limits: CPU and memory constraints based on expected load

**Environment Variables:**
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (e.g., `prod`, `staging`, `dev`)
- `SERVER_PORT`: HTTP server port (default: 8080)
- `SOLACE_HOST`, `SOLACE_PORT`, `SOLACE_VPN`: Solace connection details
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`: Database connection
- `REDIS_HOST`, `REDIS_PORT`: Redis cache connection
- External service URLs: `SECURITY_MASTER_SERVICE_URL`, `ACCOUNT_SERVICE_URL`, etc.

**Volume Mounts:**
- Configuration files (optional, if not using environment variables)
- Logs directory (for log aggregation)
- Temporary files (if needed for processing)

### Container Orchestration Considerations

**Kubernetes Deployment:**
- Deployment manifests with resource requests/limits
- Service definitions for internal and external access
- ConfigMaps for environment-specific configuration
- Secrets for sensitive data (database passwords, API keys)
- Horizontal Pod Autoscaler (HPA) based on CPU, memory, or custom metrics
- Pod Disruption Budgets for high availability

**Scaling Strategy:**
- Stateless service design enables horizontal scaling
- Partition affinity can be handled via consistent hashing or service mesh
- Consider pod anti-affinity rules to distribute instances across nodes
- Readiness and liveness probes for health monitoring

**Service Discovery:**
- Integration with service mesh (Istio, Linkerd) for advanced routing
- DNS-based service discovery for external dependencies
- Circuit breakers and retry policies at service mesh level

### Container Networking

**Network Requirements:**
- Outbound connectivity to external services (SecurityMaster, AccountService, etc.)
- Inbound connectivity for REST API (load balancer or ingress controller)
- Message queue connectivity (Solace PubSub+)
- Database connectivity (PostgreSQL or other)
- Redis cache connectivity

**Network Policies:**
- Restrict outbound connections to known service endpoints
- Implement network segmentation for security
- Consider service mesh for mTLS and advanced traffic management

### Container Security

**Security Best Practices:**
- Run container as non-root user
- Scan container images for vulnerabilities
- Use minimal base images
- Implement secrets management (Kubernetes Secrets, HashiCorp Vault)
- Enable security contexts and pod security policies
- Regular image updates and patching

**Runtime Security:**
- Resource limits to prevent resource exhaustion attacks
- Read-only root filesystem where possible
- Drop unnecessary capabilities
- Network policies to restrict communication

### Container Monitoring and Observability

**Metrics:**
- Expose Prometheus metrics endpoint
- Container-level metrics (CPU, memory, network)
- Application metrics (throughput, latency, error rates)
- JVM metrics (heap usage, GC statistics)

**Logging:**
- Structured logging (JSON format)
- Log aggregation to centralized system (ELK, Splunk, etc.)
- Log levels configurable via environment variables
- Container log rotation and retention policies

**Tracing:**
- Distributed tracing support (OpenTelemetry, Jaeger)
- Trace context propagation across service boundaries
- Correlation IDs for request tracking

### Container Lifecycle Management

**Startup:**
- Health check readiness after dependencies are available
- Graceful startup with dependency verification
- Pre-flight checks (database connectivity, message queue connectivity)

**Shutdown:**
- Graceful shutdown with in-flight request completion
- Message processing completion before termination
- Connection cleanup and resource release
- Configurable shutdown timeout

**Updates:**
- Rolling updates with zero-downtime deployment
- Blue-green or canary deployment strategies
- Database migration handling during updates
- Backward compatibility considerations

### Development and Testing with Containers

**Local Development:**
- Docker Compose for local development environment
- Integration with external services via Docker networks
- Volume mounts for hot-reload during development

**CI/CD Integration:**
- Container image building in CI pipeline
- Image scanning and security checks
- Automated testing in containerized environments
- Image registry management and versioning

**Testing:**
- Testcontainers for integration testing
- Container-based test environments
- Isolated test execution in containers
- Performance testing with containerized services

## Related Documentation

- **OpenAPI Specification**: `tradecapture/api/trade-capture-service-openapi.yaml`
- **Protobuf Schema**: `tradecapture/trade_capture_message.proto`
- **Rule Configuration Schema**: `tradecapture/rule-configuration-schema.json` (schema definition for rule structure)
- **Rule Configuration Example**: `tradecapture/rules-configuration-example.yaml` (example rule structure)
- **Data Model Design**: `tradecapture/data-model-design.md`
- **Testing Plan**: `tradecapture/testing-plan.md`
- **Cash Flow Service Design**: `cashflow_service_api_design.md`
- **Contract/Position Service Design**: `contract_position_service_api_design.md`
- **Trade State Data Model**: `trade_state_data_model.md`

**Note**: Economic and Non-Economic rules are received via API integration from external rule management service. The rule configuration schema and examples are provided for reference to understand the rule structure, but rules are managed dynamically via API endpoints rather than static configuration files.

