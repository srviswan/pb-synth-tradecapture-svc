package com.pb.synth.tradecapture.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for Hazelcast embedded instance.
 * Supports both embedded (default) and client mode.
 */
@Configuration
@ConditionalOnProperty(name = "cache.provider", havingValue = "hazelcast", matchIfMissing = false)
@Slf4j
public class HazelcastConfig {
    
    @Value("${hazelcast.cluster-name:tradecapture-cluster}")
    private String clusterName;
    
    @Value("${hazelcast.port:5701}")
    private int port;
    
    @Value("${hazelcast.members:}")
    private List<String> members;
    
    @Value("${hazelcast.mode:embedded}")
    private String mode; // embedded or client
    
    @Bean
    public HazelcastInstance hazelcastInstance() {
        if ("client".equals(mode)) {
            log.info("Creating Hazelcast client instance (connecting to cluster)");
            // For client mode, you would use HazelcastClient.newHazelcastClient()
            // This requires a separate Hazelcast cluster to be running
            throw new UnsupportedOperationException("Hazelcast client mode not yet implemented. Use embedded mode.");
        }
        
        log.info("Creating Hazelcast embedded instance: clusterName={}, port={}", clusterName, port);
        
        Config config = new Config();
        config.setClusterName(clusterName);
        
        // Network configuration
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(true);
        
        // If members are specified, configure cluster members
        if (members != null && !members.isEmpty()) {
            networkConfig.getJoin().getMulticastConfig().setEnabled(false);
            networkConfig.getJoin().getTcpIpConfig().setEnabled(true);
            networkConfig.getJoin().getTcpIpConfig().setMembers(members);
        } else {
            // Use multicast for automatic discovery (default)
            networkConfig.getJoin().getMulticastConfig().setEnabled(true);
            networkConfig.getJoin().getTcpIpConfig().setEnabled(false);
        }
        
        // Configure distributed cache map
        MapConfig mapConfig = new MapConfig();
        mapConfig.setName("distributed-cache");
        mapConfig.setTimeToLiveSeconds(3600); // Default 1 hour
        config.addMapConfig(mapConfig);
        
        // Note: CP Subsystem requires at least 3 members
        // For embedded mode (single instance), we use IMap-based locking instead
        // CP Subsystem is only enabled if we have 3+ members (cluster mode)
        if (members != null && !members.isEmpty() && members.size() >= 3) {
            config.getCPSubsystemConfig().setCPMemberCount(3);
        }
        // For embedded mode, we'll use IMap-based locking (no CP subsystem needed)
        
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        log.info("Hazelcast instance created successfully");
        
        return instance;
    }
}

