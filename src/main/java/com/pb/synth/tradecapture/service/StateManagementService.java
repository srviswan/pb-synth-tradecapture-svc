package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.PositionStatusEnum;
import com.pb.synth.tradecapture.model.State;
import com.pb.synth.tradecapture.repository.PartitionStateRepository;
import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    private final ObjectMapper objectMapper;

    /**
     * Validate state transition according to CDM rules.
     */
    public void validateStateTransition(PositionStatusEnum currentState, PositionStatusEnum newState) {
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
                // Cannot transition from these states
                break;
        }
        
        throw new IllegalStateException(
            String.format("Invalid state transition from %s to %s", currentState, newState)
        );
    }

    /**
     * Get current state for partition.
     */
    @Transactional(readOnly = true)
    public Optional<State> getState(String partitionKey) {
        return partitionStateRepository.findByPartitionKey(partitionKey)
            .map(this::convertToState);
    }

    /**
     * Update state for partition.
     */
    @Transactional
    public State updateState(String partitionKey, State newState) {
        Optional<PartitionStateEntity> existingOpt = partitionStateRepository.findByPartitionKeyWithLock(partitionKey);
        
        if (existingOpt.isPresent()) {
            PartitionStateEntity entity = existingOpt.get();
            PositionStatusEnum currentState = PositionStatusEnum.valueOf(entity.getPositionState());
            PositionStatusEnum newPositionState = newState.getPositionState();
            
            // Validate transition
            validateStateTransition(currentState, newPositionState);
            
            // Update entity
            try {
                entity.setPositionState(newPositionState.name());
                entity.setStateJson(objectMapper.writeValueAsString(newState));
                entity = partitionStateRepository.save(entity);
                return convertToState(entity);
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
                return convertToState(entity);
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

