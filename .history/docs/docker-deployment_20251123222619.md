# Docker Deployment Guide

This document provides instructions for building and deploying the PB Synthetic Trade Capture Service using Docker.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Building the Docker Image](#building-the-docker-image)
3. [Running with Docker Compose](#running-with-docker-compose)
4. [Production Deployment](#production-deployment)
5. [Environment Variables](#environment-variables)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- At least 4GB RAM available
- Ports 8080, 1433, 6379 available (or configure different ports)

## Building the Docker Image

### Build from Source

```bash
# Build the image
docker build -t pb-synth-tradecapture-svc:latest .

# Verify the image
docker images | grep pb-synth-tradecapture-svc
```

### Build with Custom Tag

```bash
docker build -t pb-synth-tradecapture-svc:1.0.0 .
docker build -t pb-synth-tradecapture-svc:latest .
```

### Multi-Architecture Build (Optional)

```bash
# Build for multiple platforms
docker buildx create --use
docker buildx build --platform linux/amd64,linux/arm64 -t pb-synth-tradecapture-svc:latest .
```

## Running with Docker Compose

### Quick Start (All Services)

```bash
# Start all services (application, database, redis)
docker-compose up -d

# View logs
docker-compose logs -f trade-capture-service

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### Development Mode

```bash
# Run with development profile and hot reload
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

### Production Mode

```bash
# Set environment variables
export DATABASE_PASSWORD=YourStrong@Passw0rd
export SPRING_PROFILES_ACTIVE=prod

# Start services
docker-compose up -d
```

## Production Deployment

### Standalone Container

```bash
# Run the service (requires external database and Redis)
docker run -d \
  --name pb-synth-tradecapture-svc \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:sqlserver://your-db-host:1433;databaseName=tradecapture \
  -e DATABASE_USERNAME=sa \
  -e DATABASE_PASSWORD=YourPassword \
  -e REDIS_HOST=your-redis-host \
  -e REDIS_PORT=6379 \
  pb-synth-tradecapture-svc:latest
```

### With External Services

If you have external MS SQL Server and Redis:

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  trade-capture-service:
    build:
      context: .
      dockerfile: Dockerfile
    image: pb-synth-tradecapture-svc:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${EXTERNAL_DB_URL}
      - DATABASE_USERNAME=${EXTERNAL_DB_USERNAME}
      - DATABASE_PASSWORD=${EXTERNAL_DB_PASSWORD}
      - REDIS_HOST=${EXTERNAL_REDIS_HOST}
      - REDIS_PORT=${EXTERNAL_REDIS_PORT}
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
```

```bash
docker-compose -f docker-compose.prod.yml up -d
```

## Environment Variables

### Required Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | MS SQL Server JDBC URL | `jdbc:sqlserver://sqlserver:1433;databaseName=tradecapture` |
| `DATABASE_USERNAME` | Database username | `sa` |
| `DATABASE_PASSWORD` | Database password | `YourStrong@Passw0rd` |
| `REDIS_HOST` | Redis host | `redis` |
| `REDIS_PORT` | Redis port | `6379` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `SERVER_PORT` | Application port | `8080` |
| `JAVA_OPTS` | Additional JVM options | (empty) |

### Setting Environment Variables

#### Using .env file

Create a `.env` file:

```bash
DATABASE_URL=jdbc:sqlserver://sqlserver:1433;databaseName=tradecapture
DATABASE_USERNAME=sa
DATABASE_PASSWORD=YourStrong@Passw0rd
REDIS_HOST=redis
REDIS_PORT=6379
SPRING_PROFILES_ACTIVE=prod
```

Docker Compose will automatically load this file.

#### Using docker-compose.yml

```yaml
services:
  trade-capture-service:
    environment:
      - DATABASE_URL=jdbc:sqlserver://sqlserver:1433;databaseName=tradecapture
      - DATABASE_USERNAME=sa
      - DATABASE_PASSWORD=YourStrong@Passw0rd
```

#### Using docker run

```bash
docker run -e DATABASE_URL=... -e DATABASE_USERNAME=... ...
```

## Health Checks

The service includes health checks:

```bash
# Check service health
curl http://localhost:8080/actuator/health

# Check from Docker
docker exec pb-synth-tradecapture-svc wget -q -O - http://localhost:8080/actuator/health
```

## Database Migrations

Flyway migrations run automatically on application startup:

```bash
# View migration status
docker-compose logs trade-capture-service | grep -i flyway

# Check database directly
docker exec -it pb-synth-tradecapture-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P YourStrong@Passw0rd \
  -Q "SELECT * FROM flyway_schema_history"
```

## Logs and Monitoring

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f trade-capture-service

# Last 100 lines
docker-compose logs --tail=100 trade-capture-service
```

### Container Status

```bash
# List running containers
docker-compose ps

# Container stats
docker stats pb-synth-tradecapture-svc
```

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker-compose logs trade-capture-service

# Check container status
docker ps -a | grep pb-synth-tradecapture-svc

# Inspect container
docker inspect pb-synth-tradecapture-svc
```

### Database Connection Issues

```bash
# Test database connection
docker exec -it pb-synth-tradecapture-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P YourStrong@Passw0rd -Q "SELECT 1"

# Check network connectivity
docker exec pb-synth-tradecapture-svc ping sqlserver
```

### Port Conflicts

```bash
# Check if ports are in use
lsof -i :8080
lsof -i :1433
lsof -i :6379

# Use different ports in docker-compose.yml
ports:
  - "8081:8080"  # Map host port 8081 to container port 8080
```

### Out of Memory

```bash
# Increase Docker memory limit
# Docker Desktop: Settings → Resources → Memory

# Or limit JVM memory in Dockerfile
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

### Rebuild After Code Changes

```bash
# Rebuild and restart
docker-compose up -d --build

# Force rebuild without cache
docker-compose build --no-cache
docker-compose up -d
```

## Publishing to Container Registry

### Docker Hub

```bash
# Login
docker login

# Tag image
docker tag pb-synth-tradecapture-svc:latest yourusername/pb-synth-tradecapture-svc:1.0.0

# Push
docker push yourusername/pb-synth-tradecapture-svc:1.0.0
```

### Azure Container Registry

```bash
# Login
az acr login --name yourregistry

# Tag
docker tag pb-synth-tradecapture-svc:latest yourregistry.azurecr.io/pb-synth-tradecapture-svc:1.0.0

# Push
docker push yourregistry.azurecr.io/pb-synth-tradecapture-svc:1.0.0
```

### AWS ECR

```bash
# Login
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin your-account.dkr.ecr.us-east-1.amazonaws.com

# Tag
docker tag pb-synth-tradecapture-svc:latest your-account.dkr.ecr.us-east-1.amazonaws.com/pb-synth-tradecapture-svc:1.0.0

# Push
docker push your-account.dkr.ecr.us-east-1.amazonaws.com/pb-synth-tradecapture-svc:1.0.0
```

## Best Practices

1. **Use Multi-stage Builds**: Reduces final image size
2. **Non-root User**: Run container as non-root user (already configured)
3. **Health Checks**: Monitor container health
4. **Resource Limits**: Set memory and CPU limits in production
5. **Secrets Management**: Use Docker secrets or external secret managers
6. **Logging**: Configure centralized logging
7. **Backup**: Regular database backups
8. **Updates**: Keep base images updated

## Security Considerations

1. **Never commit secrets**: Use environment variables or secret managers
2. **Scan images**: Regularly scan Docker images for vulnerabilities
3. **Keep updated**: Update base images regularly
4. **Network isolation**: Use Docker networks to isolate services
5. **Least privilege**: Run containers with minimal permissions

## References

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)

