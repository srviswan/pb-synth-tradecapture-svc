package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.PositionStatusEnum;
import com.pb.synth.tradecapture.model.State;
import com.pb.synth.tradecapture.repository.PartitionStateRepository;
import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import com.pb.synth.tradecapture.service.cache.PartitionStateCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing CDM-compliant state transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StateManagementService {

    private final PartitionStateRepository partitionStateRepository;
    private final PartitionStateCacheService partitionStateCacheService;
    private final ObjectMapper objectMapper;

    /**
     * Validate state transition according to CDM rules.
     */
    public void validateStateTransition(PositionStatusEnum currentState, PositionStatusEnum newState) {
        // Same state - no transition needed (allowed for idempotency)
        if (currentState == newState) {
            return;
        }
        
        // Valid transitions:
        // Executed → Formed → Settled
        // Executed → Cancelled
        // Any → Closed
        
        if (newState == PositionStatusEnum.CLOSED) {
            // Any state can transition to Closed
            return;
        }
        
        switch (currentState) {
            case EXECUTED:
                if (newState == PositionStatusEnum.FORMED || newState == PositionStatusEnum.CANCELLED) {
                    return;
                }
                break;
            case FORMED:
                if (newState == PositionStatusEnum.SETTLED) {
                    return;
                }
                break;
            case SETTLED:
            case CANCELLED:
            case CLOSED:
                // Cannot transition from these states (except to CLOSED)
                break;
        }
        
        throw new IllegalStateException(
            String.format("Invalid state transition from %s to %s", currentState, newState)
        );
    }

    /**
     * Get current state for partition.
     * Uses Redis cache (L1) first, then database (L2) as fallback.
     */
    @Transactional(readOnly = true)
    public Optional<State> getState(String partitionKey) {
        // Try cache first (L1)
        Optional<State> cached = partitionStateCacheService.get(partitionKey);
        if (cached.isPresent()) {
            log.debug("Partition state cache hit: {}", partitionKey);
            return cached;
        }
        
        // Fallback to database (L2)
        Optional<State> state = partitionStateRepository.findByPartitionKey(partitionKey)
            .map(this::convertToState);
        
        // Cache the result for future lookups
        state.ifPresent(s -> partitionStateCacheService.put(partitionKey, s));
        
        return state;
    }

    /**
     * Update state for partition.
     * Uses REQUIRES_NEW to isolate deadlocks from outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public State updateState(String partitionKey, State newState) {
        Optional<PartitionStateEntity> existingOpt = partitionStateRepository.findByPartitionKeyWithLock(partitionKey);
        
        if (existingOpt.isPresent()) {
            PartitionStateEntity entity = existingOpt.get();
            PositionStatusEnum currentState = PositionStatusEnum.valueOf(entity.getPositionState());
            PositionStatusEnum newPositionState = newState.getPositionState();
            
            // Only validate transition if state is actually changing
            if (currentState != newPositionState) {
                validateStateTransition(currentState, newPositionState);
            }
            
            // Update entity (even if state is same, update JSON and timestamp)
            try {
                entity.setPositionState(newPositionState.name());
                entity.setStateJson(objectMapper.writeValueAsString(newState));
                entity = partitionStateRepository.save(entity);
                State updatedState = convertToState(entity);
                
                // Update cache
                partitionStateCacheService.put(partitionKey, updatedState);
                
                return updatedState;
            } catch (Exception e) {
                log.error("Error updating state", e);
                throw new RuntimeException("Failed to update state", e);
            }
        } else {
            // Create new state
            try {
                PartitionStateEntity entity = PartitionStateEntity.builder()
                    .partitionKey(partitionKey)
                    .positionState(newState.getPositionState().name())
                    .stateJson(objectMapper.writeValueAsString(newState))
                    .lastSequenceNumber(0L)
                    .build();
                entity = partitionStateRepository.save(entity);
                State createdState = convertToState(entity);
                
                // Cache the new state
                partitionStateCacheService.put(partitionKey, createdState);
                
                return createdState;
            } catch (Exception e) {
                log.error("Error creating state", e);
                throw new RuntimeException("Failed to create state", e);
            }
        }
    }

    private State convertToState(PartitionStateEntity entity) {
        try {
            if (entity.getStateJson() != null) {
                return objectMapper.readValue(entity.getStateJson(), State.class);
            } else {
                // Fallback to basic state
                return State.builder()
                    .positionState(PositionStatusEnum.valueOf(entity.getPositionState()))
                    .build();
            }
        } catch (Exception e) {
            log.error("Error converting PartitionStateEntity to State", e);
            // Fallback
            return State.builder()
                .positionState(PositionStatusEnum.valueOf(entity.getPositionState()))
                .build();
        }
    }
}

