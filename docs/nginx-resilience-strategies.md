# Nginx Resilience Strategies

## Current Situation

Currently, there is **one nginx instance** configured in `docker-compose.scale.yml`, which creates a **single point of failure**. If nginx goes down, the entire service becomes unavailable.

## Resilience Strategies

### Strategy 1: Multiple Nginx Instances with HAProxy (Recommended for Docker)

**Architecture:**
```
Client
  ↓
HAProxy (High Availability Load Balancer)
  ├─→ nginx-1 (port 80)
  └─→ nginx-2 (port 80)
      ↓
  Backend Services
```

**Benefits:**
- ✅ Active-Active: Both nginx instances handle traffic
- ✅ Automatic failover if one nginx fails
- ✅ Health checks ensure only healthy instances receive traffic
- ✅ Works well with Docker Compose

**Implementation:**

1. **HAProxy Configuration** (`haproxy.cfg`):
```haproxy
global
    daemon
    maxconn 4096

defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms
    option forwardfor
    option httpchk GET /health

frontend http_front
    bind *:8080
    default_backend nginx_backend

backend nginx_backend
    balance roundrobin
    option httpchk GET /health
    server nginx1 nginx-1:80 check inter 3s fall 3 rise 2
    server nginx2 nginx-2:80 check inter 3s fall 3 rise 2
```

2. **Updated docker-compose.scale.yml** (Add HAProxy + Multiple nginx):
```yaml
services:
  # High Availability Load Balancer
  haproxy:
    image: haproxy:alpine
    container_name: pb-synth-tradecapture-haproxy
    ports:
      - "8080:8080"
    volumes:
      - ./haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
    depends_on:
      - nginx-1
      - nginx-2
    networks:
      - tradecapture-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "haproxy", "-c", "-f", "/usr/local/etc/haproxy/haproxy.cfg"]
      interval: 10s
      timeout: 3s
      retries: 3

  # Nginx Instance 1
  nginx-1:
    image: nginx:alpine
    container_name: pb-synth-tradecapture-nginx-1
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - trade-capture-service-1
      - trade-capture-service-2
      - trade-capture-service-3
    networks:
      - tradecapture-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:80/health"]
      interval: 5s
      timeout: 2s
      retries: 3

  # Nginx Instance 2
  nginx-2:
    image: nginx:alpine
    container_name: pb-synth-tradecapture-nginx-2
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - trade-capture-service-1
      - trade-capture-service-2
      - trade-capture-service-3
    networks:
      - tradecapture-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:80/health"]
      interval: 5s
      timeout: 2s
      retries: 3

  # ... rest of services remain same
```

---

### Strategy 2: Keepalived for VIP Failover (Active-Passive)

**Architecture:**
```
Client
  ↓
Virtual IP (VIP: 10.0.0.100)
  ├─→ nginx-1 (Master/Active) ← Currently serving
  └─→ nginx-2 (Backup/Standby) ← Takes over if master fails
      ↓
  Backend Services
```

**Benefits:**
- ✅ Active-Passive: One active, one standby
- ✅ Virtual IP automatically moves to backup
- ✅ Fast failover (typically < 3 seconds)
- ✅ Good for infrastructure-level redundancy

**Limitations:**
- ⚠️ More complex setup (requires network configuration)
- ⚠️ Only one nginx handles traffic at a time
- ⚠️ Requires shared network infrastructure

**Best For:** On-premises or bare-metal deployments

---

### Strategy 3: DNS-Based Load Balancing (Multiple IPs)

**Architecture:**
```
Client DNS Query
  ↓
DNS Server Returns Multiple A Records:
  - nginx1.example.com → 10.0.0.10
  - nginx2.example.com → 10.0.0.11
  ↓
Client connects to one of them
```

**Benefits:**
- ✅ Simple: Uses standard DNS
- ✅ No additional load balancer needed
- ✅ Built-in client-side load distribution

**Limitations:**
- ⚠️ No automatic failover (DNS TTL delay)
- ⚠️ Client must implement retry logic
- ⚠️ DNS caching can delay failover

**Best For:** Public cloud with multiple zones/regions

---

### Strategy 4: Kubernetes Deployment (Production)

**Architecture:**
```
Kubernetes Service (LoadBalancer)
  ↓
Nginx Deployment (Replicas: 3)
  ├─→ nginx-pod-1
  ├─→ nginx-pod-2
  └─→ nginx-pod-3
      ↓
  Backend Services
```

**Benefits:**
- ✅ Auto-scaling based on load
- ✅ Self-healing (K8s restarts failed pods)
- ✅ Rolling updates (zero downtime)
- ✅ Built-in service discovery

**Implementation Example:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-lb
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx-lb
  template:
    metadata:
      labels:
        app: nginx-lb
    spec:
      containers:
      - name: nginx
        image: nginx:alpine
        ports:
        - containerPort: 80
        volumeMounts:
        - name: nginx-config
          mountPath: /etc/nginx/nginx.conf
          subPath: nginx.conf
        livenessProbe:
          httpGet:
            path: /health
            port: 80
          initialDelaySeconds: 10
          periodSeconds: 5
        readinessProbe:
          httpGet:
            path: /health
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  name: nginx-lb-service
spec:
  type: LoadBalancer
  ports:
  - port: 8080
    targetPort: 80
  selector:
    app: nginx-lb
```

---

### Strategy 5: Cloud Load Balancers (AWS, GCP, Azure)

**Architecture:**
```
Cloud Load Balancer (ALB/GLB/ALB)
  ├─→ nginx-1 (Availability Zone 1)
  └─→ nginx-2 (Availability Zone 2)
      ↓
  Backend Services
```

**Benefits:**
- ✅ Managed service (no maintenance)
- ✅ Built-in health checks
- ✅ SSL termination
- ✅ Automatic failover across zones

**Examples:**
- **AWS**: Application Load Balancer (ALB)
- **GCP**: Google Cloud Load Balancer
- **Azure**: Azure Application Gateway

---

## Recommended Approach for This Project

### For Development/Testing: Strategy 1 (HAProxy + Multiple Nginx)

**Why:**
- ✅ Easy to implement with Docker Compose
- ✅ Active-Active redundancy
- ✅ Good for local/development environments

### For Production: Strategy 4 (Kubernetes) or Strategy 5 (Cloud LB)

**Why:**
- ✅ Auto-scaling and self-healing
- ✅ Production-grade reliability
- ✅ Zero-downtime deployments

---

## Implementation Steps for HAProxy Approach

### Step 1: Create HAProxy Configuration

Create `haproxy.cfg`:

```haproxy
global
    daemon
    maxconn 4096
    log stdout local0

defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms
    option forwardfor
    option httpchk GET /health
    option httplog
    log global

frontend http_front
    bind *:8080
    default_backend nginx_backend
    stats enable
    stats uri /haproxy-stats
    stats refresh 30s

backend nginx_backend
    balance roundrobin
    option httpchk GET /health
    http-check expect status 200
    server nginx1 nginx-1:80 check inter 3s fall 3 rise 2
    server nginx2 nginx-2:80 check inter 3s fall 3 rise 2
```

### Step 2: Update docker-compose.scale.yml

Add HAProxy and multiple nginx instances (see Strategy 1 above).

### Step 3: Update nginx.conf for Health Checks

Ensure nginx has a health check endpoint (already exists in your config).

### Step 4: Test Resilience

```bash
# Start services
docker-compose -f docker-compose.scale.yml up -d

# Check HAProxy stats
curl http://localhost:8080/haproxy-stats

# Kill one nginx instance
docker stop pb-synth-tradecapture-nginx-1

# Verify traffic continues through nginx-2
curl http://localhost:8080/api/v1/health

# Restart nginx-1
docker start pb-synth-tradecapture-nginx-1
```

---

## Health Check Configuration

### Nginx Health Check Endpoint

Already configured in `nginx.conf`:
```nginx
location /health {
    access_log off;
    proxy_pass http://trade_capture_backend;
}
```

### Additional Resilience Configurations

1. **Nginx Worker Processes**: Handle more connections
```nginx
events {
    worker_connections 2048;  # Increase from 1024
    use epoll;  # Efficient event model
}

worker_processes auto;  # Auto-detect CPU cores
```

2. **Connection Pooling**: Reuse connections to backends
```nginx
upstream trade_capture_backend {
    least_conn;
    keepalive 32;  # Keep 32 idle connections
    # ... servers
}
```

3. **Retry Logic**: Already configured
```nginx
proxy_next_upstream error timeout http_500 http_502 http_503 http_504;
proxy_next_upstream_tries 3;
```

---

## Monitoring and Alerting

### Key Metrics to Monitor:

1. **Nginx Availability**
   - Uptime percentage
   - Health check failures
   - Response times

2. **Backend Health**
   - Backend server availability
   - Failed requests
   - Connection pool usage

3. **Load Balancer Health**
   - Active connections
   - Requests per second
   - Failover events

### Tools:
- **Prometheus + Grafana**: For metrics
- **ELK Stack**: For log aggregation
- **Health check scripts**: Automated monitoring

---

## Summary

**Current State**: Single nginx instance (SPOF)

**Recommended**: 
- **Short-term**: Add HAProxy with 2 nginx instances
- **Long-term**: Move to Kubernetes or cloud load balancer

**Key Principles**:
1. ✅ **Multiple instances**: Never rely on a single load balancer
2. ✅ **Health checks**: Automatic detection of failures
3. ✅ **Fast failover**: < 5 seconds for automatic recovery
4. ✅ **Monitoring**: Alert on failures and degraded performance

