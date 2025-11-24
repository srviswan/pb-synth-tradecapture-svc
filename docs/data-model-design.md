# PB Synthetic Trade Capture Service Data Model Design

## Overview

This document describes the data models used in the pb-synth-tradecapture-svc (PB Synthetic Trade Capture Service). The models are designed to be Java POJOs inspired by CDM patterns, using CDM classes where appropriate but wrapping them in service-specific structures.

## Design Principles

1. **CDM Compatibility**: Use CDM classes (TradeLot, EconomicTerms, PerformancePayout, InterestPayout, State) where applicable
2. **Service-Specific Wrappers**: Wrap CDM classes in service-specific POJOs for business logic
3. **Partition Awareness**: Include partition key for sequencing
4. **State Management**: Include CDM-compliant state information
5. **Workflow Support**: Include workflow status and enrichment status

## Core Data Models

### TradeCaptureRequest (Input)

Represents incoming trade lots from upstream systems (front office trading and allocation management).

**Fields:**
- `tradeId` (String): Unique trade identifier (required, used for idempotency)
- `idempotencyKey` (String, optional): Client-provided idempotency key (if not provided, tradeId is used)
- `accountId` (String): Account identifier (part of partition key)
- `bookId` (String): Book identifier (part of partition key)
- `securityId` (String): Security identifier - ISIN, CUSIP, etc. (part of partition key)
- `source` (TradeSource enum): Trade source - AUTOMATED or MANUAL
- `tradeLots` (List<TradeLot>): List of trade lots (CDM TradeLot)
- `tradeDate` (LocalDate): Trade date
- `counterpartyIds` (List<String>): List of counterparty identifiers (typically 2 parties)
- `metadata` (Map<String, Object>): Additional metadata (key-value pairs)

**Idempotency:**
- `tradeId` must be unique across the system
- Service checks for duplicate `tradeId` before processing
- Duplicate trades within idempotency window return cached result
- See service design document for detailed idempotency strategy

**Manual Entry Specific Fields** (only populated if source = MANUAL):
- `enteredBy` (String): User ID who entered the trade
- `entryTimestamp` (LocalDate): Timestamp when trade was entered

**Computed Fields:**
- `partitionKey` (String): Generated as `{accountId}_{bookId}_{securityId}`

**CDM Classes Used:**
- `TradeLot` (CDM): Contains lotIdentifier and priceQuantity

### SwapBlotter (Output)

Java POJO inspired by CDM TradeState, containing fully enriched contract with EconomicTerms, PerformancePayout, and InterestPayout details.

**Fields:**
- `tradeId` (String): Unique trade identifier
- `partitionKey` (String): Partition key (Account/Book + Security) for sequencing
- `tradeLots` (List<TradeLot>): List of trade lots (CDM TradeLot)
- `contract` (Contract): Contract (NonTransferableProduct wrapper)
- `state` (State): State (CDM State with PositionStatusEnum)
- `enrichmentStatus` (EnrichmentStatus enum): Enrichment status
- `workflowStatus` (WorkflowStatus enum): Workflow status determined by rules
- `processingMetadata` (ProcessingMetadata): Processing metadata

**CDM Classes Used:**
- `TradeLot` (CDM): Trade lots
- `NonTransferableProduct` (CDM): Wrapped in Contract
- `State` (CDM): With PositionStatusEnum

### Contract

Wrapper around CDM NonTransferableProduct containing product identification, taxonomy, and economic terms.

**Fields:**
- `identifier` (List<Identifier>): Product identifiers
- `taxonomy` (List<ProductTaxonomy>): Product taxonomy (primary asset class, etc.)
- `economicTerms` (EconomicTerms): Economic terms including payouts

**CDM Classes Used:**
- `NonTransferableProduct` (CDM): Base product structure
- `ProductIdentifier` (CDM): Product identifiers
- `ProductTaxonomy` (CDM): Product taxonomy

### EconomicTerms

CDM EconomicTerms containing payout information.

**Fields:**
- `effectiveDate` (AdjustableOrRelativeDate): Effective date
- `terminationDate` (AdjustableOrRelativeDate): Termination date
- `payout` (List<Payout>): List of payouts

**CDM Classes Used:**
- `EconomicTerms` (CDM): Base economic terms
- `Payout` (CDM): Union type containing PerformancePayout, InterestPayout, etc.

### PerformancePayout

CDM PerformancePayout representing the equity leg of an equity swap.

**Fields:**
- `payerReceiver` (PayerReceiver): Payer and receiver parties
- `priceQuantity` (PriceQuantity): Price and quantity information
- `underlier` (Underlier): Underlier information (single name, index, or basket)
- `returnTerms` (ReturnTerms): Return terms (Price vs Total return, dividend handling)

**CDM Classes Used:**
- `PerformancePayout` (CDM): Base performance payout
- `PayerReceiver` (CDM): Payer/receiver information
- `PriceQuantity` (CDM): Price and quantity
- `Underlier` (CDM): Underlier (single, index, or basket)
- `ReturnTerms` (CDM): Return terms

### InterestPayout

CDM InterestRatePayout representing the interest/funding leg of an equity swap.

**Fields:**
- `payerReceiver` (PayerReceiver): Payer and receiver parties
- `priceQuantity` (PriceQuantity): Price and quantity information
- `fixedRate` (BigDecimal): Fixed rate (if fixed rate leg)
- `floatingRate` (FloatingRateSpec): Floating rate specification (if floating rate leg)
- `dayCountFraction` (DayCountFraction): Day count convention

**CDM Classes Used:**
- `InterestRatePayout` (CDM): Base interest rate payout
- `PayerReceiver` (CDM): Payer/receiver information
- `PriceQuantity` (CDM): Price and quantity
- `DayCountFraction` (CDM): Day count convention

### State

CDM State with PositionStatusEnum for tracking trade lifecycle state.

**Fields:**
- `positionState` (PositionStatusEnum): Position state
  - `Executed`: Trade has been executed
  - `Formed`: Contract has been formed
  - `Settled`: Position has settled
  - `Cancelled`: Position has been cancelled
  - `Closed`: Position has been closed
- `closedState` (ClosedState): Closed state information (if position is closed)

**CDM Classes Used:**
- `State` (CDM): Base state
- `PositionStatusEnum` (CDM): Position status enumeration
- `ClosedState` (CDM): Closed state information

### ProcessingMetadata

Metadata about the processing of a trade.

**Fields:**
- `processedAt` (ZonedDateTime): Timestamp when processed
- `processingTimeMs` (Long): Processing time in milliseconds
- `rulesApplied` (List<String>): List of rule IDs that were applied
- `enrichmentSources` (List<String>): Sources used for enrichment (SecurityMaster, AccountService, etc.)

## Enumerations

### TradeSource

- `AUTOMATED`: Trade from automated system (STP)
- `MANUAL`: Trade manually entered via manual entry screen

### EnrichmentStatus

- `COMPLETE`: All enrichment completed successfully
- `PARTIAL`: Partial enrichment (some fields missing)
- `FAILED`: Enrichment failed
- `PENDING`: Enrichment pending

### WorkflowStatus

- `APPROVED`: Trade approved for STP processing
- `PENDING_APPROVAL`: Trade requires manual approval
- `REJECTED`: Trade rejected (by rules or validation)

### ProcessingStatus

- `SUCCESS`: Processing completed successfully
- `FAILED`: Processing failed
- `PARTIAL`: Partial processing (some steps failed)
- `PENDING`: Processing pending

## Protobuf Message Models

### TradeCaptureMessage (Input)

Protobuf message for queue-based processing.

**Key Fields:**
- `trade_id`: Trade identifier
- `account_id`, `book_id`, `security_id`: Partition key components
- `partition_key`: Computed partition key
- `sequence_number`: Sequence number for ordering
- `source`: Trade source (AUTOMATED or MANUAL)
- `trade_date`: Trade date
- `timestamp`: Message creation timestamp
- `trade_lots`: List of trade lots
- `counterparty_ids`: Counterparty identifiers
- `metadata`: Additional metadata
- `manual_entry_info`: Manual entry information (if source = MANUAL)

See: `trade_capture_message.proto` for complete schema.

### SwapBlotterMessage (Output)

Protobuf message for output queue.

**Key Fields:**
- `trade_id`: Trade identifier
- `partition_key`: Partition key
- `trade_lots`: Trade lots
- `contract`: Contract information
- `state`: State information
- `enrichment_status`: Enrichment status
- `workflow_status`: Workflow status
- `processing_metadata`: Processing metadata
- `status`: Processing status
- `error`: Error information (if processing failed)

See: `trade_capture_message.proto` for complete schema.

## Data Flow

### Input Flow

1. **TradeCaptureRequest** (REST API) or **TradeCaptureMessage** (Solace queue)
2. Extract partition key: `{accountId}_{bookId}_{securityId}`
3. Enrich with reference data (Security, Account)
4. Apply rules (Economic, Non-Economic, Workflow)
5. Validate and update state
6. Generate **SwapBlotter**

### Output Flow

1. **SwapBlotter** (Java POJO)
2. Identify active subscribers (from configuration/registry)
3. For each subscriber:
   - **Async Messaging**: Serialize to **SwapBlotterMessage** (protobuf) and publish to configured queue/topic
     - Solace PubSub+: `trade/capture/blotter`
     - Kafka: Configured topic
     - RabbitMQ: Configured exchange/queue
     - Other messaging systems as configured
   - **API-Based**: Serialize to JSON and deliver via:
     - Webhook HTTP POST to configured URLs
     - REST API POST to downstream service endpoints
     - gRPC streaming (if configured)
4. Return **TradeCaptureResponse** (REST API) or acknowledge queue message
5. Track delivery status per subscriber for monitoring and retry

**Multiple Subscribers**:
- Each SwapBlotter can be delivered to multiple downstream services simultaneously
- Subscribers can use different delivery mechanisms (queue, webhook, REST, gRPC)
- Independent failure handling: failure in one subscriber doesn't affect others
- Delivery guarantees vary by mechanism (at-least-once for queues, best-effort for APIs)

## Partition Key Strategy

### Format

`{accountId}_{bookId}_{securityId}`

### Purpose

- Ensures trades for the same Account/Book + Security are processed sequentially
- Enables parallel processing of different partitions
- Maintains state consistency per partition

### Example

- Account: `ACC-001`
- Book: `BOOK-001`
- Security: `US0378331005`
- Partition Key: `ACC-001_BOOK-001_US0378331005`

## State Management

### State Transitions

Valid transitions (CDM-compliant):
- `Executed` → `Formed` → `Settled`
- `Executed` → `Cancelled`
- Any → `Closed`

### State Storage

- State stored per partition key
- Version numbers for optimistic locking
- Distributed locks for state updates

## Related Documentation

- **Service Design**: `trade_capture_service_design.md`
- **OpenAPI Specification**: `api/trade-capture-service-openapi.yaml`
- **Protobuf Schema**: `trade_capture_message.proto`
- **CDM TradeState**: `../trade_state_data_model.md`

