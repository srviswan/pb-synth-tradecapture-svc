# Flyway Rollback Strategy for CI/CD

This document outlines the strategies and best practices for handling database migrations and rollbacks using Flyway in our CI/CD pipelines.

## Table of Contents

1. [Overview](#overview)
2. [Flyway Rollback Philosophy](#flyway-rollback-philosophy)
3. [Rollback Strategies](#rollback-strategies)
4. [CI/CD Implementation](#cicd-implementation)
5. [Best Practices](#best-practices)
6. [Emergency Procedures](#emergency-procedures)

## Overview

Flyway uses a **forward-only migration model** - it does not provide automatic rollback capabilities like some other migration tools. This document explains how we handle rollbacks in our CI/CD pipelines for the PB Synthetic Trade Capture Service.

### Key Principles

- **Prevention First**: Test migrations thoroughly before production
- **Backup Always**: Automate database backups before migrations
- **Reversible Design**: Design migrations to be reversible when possible
- **Feature Flags**: Use feature flags to make code changes reversible
- **Monitoring**: Monitor deployments and have automated recovery procedures

## Flyway Rollback Philosophy

### Why No Automatic Rollbacks?

Flyway's design philosophy emphasizes:

1. **Immutability**: Once a migration is applied, it should not be changed
2. **Safety**: Prevents accidental data loss from rollbacks
3. **Simplicity**: Forward-only model is easier to understand and maintain
4. **Production Safety**: Rollbacks can be dangerous with production data

### Migration Tracking

Flyway maintains the `flyway_schema_history` table which tracks:
- Version number
- Description
- Type (SQL, JDBC, etc.)
- Checksum (validates file integrity)
- Installed timestamp
- Success status

## Rollback Strategies

### 1. Prevention-First Approach (Recommended)

**Strategy**: Test migrations thoroughly before production deployment.

#### Pipeline Stages

```
1. Build → Compile code
2. Test → Run unit/integration tests
3. Migration Test → Test migrations against test database
4. Deploy to Staging → Apply migrations
5. Integration Tests → Verify application works
6. Deploy to Production → Apply migrations (only if staging passes)
```

#### Key Points

- Test migrations in CI before production
- Use test database that mirrors production structure
- Fail pipeline if migrations fail
- Never skip testing stages

### 2. Blue-Green Deployment Pattern

**Strategy**: Keep old version running during deployment.

#### How It Works

```
Production Environment:
- Blue (current): Running V1, V2, V3 migrations
- Green (new): Running V1, V2, V3, V4 migrations

Deployment Flow:
1. Deploy new version to Green environment
2. Run Flyway migrations on Green
3. Run smoke tests on Green
4. If tests pass → Switch traffic to Green
5. If tests fail → Keep traffic on Blue (rollback = don't switch)
```

**Rollback**: Simply keep old version running; no database rollback needed.

#### Benefits

- Zero-downtime deployments
- Instant rollback (just switch traffic back)
- No database rollback required
- Safe for production

### 3. Database Backup and Restore

**Strategy**: Automated backups before migrations with restore capability.

#### CI/CD Pipeline Integration

```yaml
# Example pipeline stages

stages:
  - backup
  - migrate
  - test
  - rollback (if needed)

backup_database:
  script:
    - sqlcmd -S $DB_SERVER -d $DB_NAME -Q "BACKUP DATABASE [$DB_NAME] TO DISK='backup.bak'"
    - aws s3 cp backup.bak s3://backups/pre-migration-${CI_PIPELINE_ID}.bak

run_migrations:
  script:
    - mvn flyway:migrate
  on_failure:
    - restore_database

restore_database:
  script:
    - aws s3 cp s3://backups/pre-migration-${CI_PIPELINE_ID}.bak backup.bak
    - sqlcmd -S $DB_SERVER -d $DB_NAME -Q "RESTORE DATABASE [$DB_NAME] FROM DISK='backup.bak' WITH REPLACE"
```

#### Backup Best Practices

- **Automated**: Always backup before migrations
- **Versioned**: Store backups with pipeline ID or timestamp
- **Tested**: Regularly test restore procedures
- **Retention**: Keep backups for reasonable period (7-30 days)

### 4. Feature Flags for Reversible Changes

**Strategy**: Make changes reversible via configuration flags.

#### Example

```sql
-- V2__add_new_column.sql
ALTER TABLE swap_blotter ADD new_feature_column VARCHAR(100);
GO
```

```java
// Application code uses feature flag
if (featureFlag.isEnabled("newFeature")) {
    // Use new_feature_column
} else {
    // Use old logic
}
```

**Rollback**: Simply disable feature flag, don't rollback migration.

**Permanent Removal**: Only create reverse migration if feature is permanently removed:

```sql
-- V3__remove_new_column.sql (only if feature is permanently removed)
ALTER TABLE swap_blotter DROP COLUMN new_feature_column;
GO
```

### 5. Staged Migration Approach

**Strategy**: Split migrations into safe and risky categories.

#### Migration Types

**Safe Migrations** (always apply automatically):
- Adding nullable columns
- Adding indexes
- Adding new tables
- Adding stored procedures
- Non-destructive changes

**Risky Migrations** (require manual approval):
- Dropping columns
- Changing data types
- Data migrations
- Dropping tables
- Destructive changes

#### Pipeline Implementation

```yaml
safe_migrations:
  script:
    - mvn flyway:migrate -Dflyway.locations=classpath:db/migration/safe
  auto_deploy: true

risky_migrations:
  script:
    - mvn flyway:migrate -Dflyway.locations=classpath:db/migration/risky
  auto_deploy: false
  requires_approval: true
```

### 6. Reverse Migration Scripts (Manual Approach)

**Strategy**: Maintain rollback scripts separately from forward migrations.

#### Project Structure

```
db/
  migration/
    V1__initial_schema.sql
    V2__add_feature.sql
  rollback/
    R1__rollback_v2.sql  (manually created)
    R2__rollback_v3.sql
```

#### CI/CD Integration

```yaml
rollback_migration:
  script:
    - |
      # Determine which migration to rollback
      LAST_MIGRATION=$(mvn flyway:info | grep "Pending" | tail -1)
      
      # Run corresponding rollback script
      if [ -f "db/rollback/R${LAST_MIGRATION}__rollback.sql" ]; then
        mvn flyway:migrate -Dflyway.locations=classpath:db/rollback
      else
        echo "No rollback script found, using database restore"
        # Fall back to backup restore
      fi
```

**Note**: This approach requires manual creation and maintenance of rollback scripts.

## CI/CD Implementation

### GitHub Actions Example

```yaml
name: Deploy with Flyway

on:
  push:
    branches: [main]

jobs:
  test-migrations:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Test Migrations
        run: |
          # Start test database
          docker run -d -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=Test123!" \
            -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
          
          # Wait for DB
          sleep 30
          
          # Run migrations
          mvn flyway:migrate \
            -Dflyway.url=jdbc:sqlserver://localhost:1433 \
            -Dflyway.user=sa \
            -Dflyway.password=Test123!
          
          # Run tests
          mvn test
          
          # If tests fail, migrations are not applied to production

  deploy-staging:
    needs: test-migrations
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Backup Staging DB
        run: |
          # Backup before migration
          sqlcmd -S $STAGING_DB_SERVER -d staging_db \
            -Q "BACKUP DATABASE staging_db TO DISK='staging-backup.bak'"
      
      - name: Run Migrations on Staging
        run: |
          mvn flyway:migrate \
            -Dflyway.url=$STAGING_DB_URL \
            -Dflyway.user=$STAGING_DB_USER \
            -Dflyway.password=$STAGING_DB_PASSWORD
      
      - name: Run Integration Tests
        run: mvn verify
      
      - name: Rollback on Failure
        if: failure()
        run: |
          # Restore from backup
          sqlcmd -S $STAGING_DB_SERVER -d staging_db \
            -Q "RESTORE DATABASE staging_db FROM DISK='staging-backup.bak' WITH REPLACE"

  deploy-production:
    needs: deploy-staging
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v3
      - name: Backup Production DB
        run: |
          sqlcmd -S $PROD_DB_SERVER -d prod_db \
            -Q "BACKUP DATABASE prod_db TO DISK='prod-backup-$(date +%Y%m%d-%H%M%S).bak'"
      
      - name: Run Migrations on Production
        run: |
          mvn flyway:migrate \
            -Dflyway.url=$PROD_DB_URL \
            -Dflyway.user=$PROD_DB_USER \
            -Dflyway.password=$PROD_DB_PASSWORD
      
      - name: Health Check
        run: |
          # Verify application is working
          curl -f https://api.production.com/health || exit 1
      
      - name: Rollback on Failure
        if: failure()
        run: |
          # Restore from backup
          sqlcmd -S $PROD_DB_SERVER -d prod_db \
            -Q "RESTORE DATABASE prod_db FROM DISK='prod-backup-*.bak' WITH REPLACE"
```

### GitLab CI Example

```yaml
stages:
  - test
  - deploy-staging
  - deploy-production

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"

test-migrations:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  services:
    - name: mcr.microsoft.com/mssql/server:2022-latest
      alias: sqlserver
      variables:
        ACCEPT_EULA: "Y"
        SA_PASSWORD: "Test123!"
  script:
    - sleep 30  # Wait for SQL Server
    - mvn flyway:migrate -Dflyway.url=jdbc:sqlserver://sqlserver:1433
    - mvn test
  only:
    - merge_requests
    - main

deploy-staging:
  stage: deploy-staging
  image: maven:3.9-eclipse-temurin-21
  script:
    - |
      # Backup staging database
      sqlcmd -S $STAGING_DB_SERVER -d $STAGING_DB_NAME \
        -U $STAGING_DB_USER -P $STAGING_DB_PASSWORD \
        -Q "BACKUP DATABASE [$STAGING_DB_NAME] TO DISK='/tmp/staging-backup.bak'"
    
    - mvn flyway:migrate \
        -Dflyway.url=$STAGING_DB_URL \
        -Dflyway.user=$STAGING_DB_USER \
        -Dflyway.password=$STAGING_DB_PASSWORD
    
    - mvn verify
  on_failure:
    - |
      # Restore from backup
      sqlcmd -S $STAGING_DB_SERVER -d $STAGING_DB_NAME \
        -U $STAGING_DB_USER -P $STAGING_DB_PASSWORD \
        -Q "RESTORE DATABASE [$STAGING_DB_NAME] FROM DISK='/tmp/staging-backup.bak' WITH REPLACE"
  only:
    - main
  environment:
    name: staging

deploy-production:
  stage: deploy-production
  image: maven:3.9-eclipse-temurin-21
  script:
    - |
      # Backup production database
      BACKUP_FILE="prod-backup-$(date +%Y%m%d-%H%M%S).bak"
      sqlcmd -S $PROD_DB_SERVER -d $PROD_DB_NAME \
        -U $PROD_DB_USER -P $PROD_DB_PASSWORD \
        -Q "BACKUP DATABASE [$PROD_DB_NAME] TO DISK='/tmp/$BACKUP_FILE'"
      
      # Upload backup to S3
      aws s3 cp /tmp/$BACKUP_FILE s3://$BACKUP_BUCKET/$BACKUP_FILE
    
    - mvn flyway:migrate \
        -Dflyway.url=$PROD_DB_URL \
        -Dflyway.user=$PROD_DB_USER \
        -Dflyway.password=$PROD_DB_PASSWORD
    
    - |
      # Health check
      for i in {1..10}; do
        if curl -f https://api.production.com/health; then
          echo "Health check passed"
          exit 0
        fi
        sleep 10
      done
      echo "Health check failed"
      exit 1
  on_failure:
    - |
      # Restore from backup
      LATEST_BACKUP=$(aws s3 ls s3://$BACKUP_BUCKET/ | sort | tail -1 | awk '{print $4}')
      aws s3 cp s3://$BACKUP_BUCKET/$LATEST_BACKUP /tmp/restore.bak
      sqlcmd -S $PROD_DB_SERVER -d $PROD_DB_NAME \
        -U $PROD_DB_USER -P $PROD_DB_PASSWORD \
        -Q "RESTORE DATABASE [$PROD_DB_NAME] FROM DISK='/tmp/restore.bak' WITH REPLACE"
  only:
    - main
  when: manual
  environment:
    name: production
```

## Best Practices

### 1. Always Test Migrations in CI First

- Use test database that mirrors production structure
- Run full test suite after migrations
- Fail pipeline if migrations fail
- Never skip testing stages

### 2. Backup Before Production Migrations

- Automated backup in CI/CD pipeline
- Store backups in versioned location (S3, Azure Blob, etc.)
- Test restore procedures regularly
- Keep backups for reasonable period (7-30 days)

### 3. Use Staging Environment

- Apply migrations to staging first
- Test thoroughly before production
- Use staging as final validation
- Only proceed to production if staging passes

### 4. Monitor After Deployment

- Health checks after migration
- Metrics monitoring (error rate, latency, etc.)
- Alert on failures
- Automated rollback triggers

### 5. Have a Rollback Plan

- Document rollback procedure
- Test rollback process regularly
- Keep rollback scripts ready
- Train team on rollback procedures

### 6. Use Feature Flags

- Make code changes reversible
- Don't rollback database, just disable feature
- Gradual rollout with feature flags
- Monitor feature usage

### 7. Split Risky Migrations

- Separate data migrations from schema changes
- Apply data migrations separately
- Test data migrations with production-like data
- Use transactions for data migrations

### 8. Design Migrations to be Reversible

When possible, design migrations that can be easily reversed:

```sql
-- Good: Adding nullable column (easily reversible)
ALTER TABLE swap_blotter ADD new_column VARCHAR(100) NULL;
GO

-- Better: Use feature flag instead of column removal
-- If needed later:
-- ALTER TABLE swap_blotter DROP COLUMN new_column;
GO
```

## Emergency Procedures

### Production Migration Failure

If a migration fails in production:

1. **Immediate Actions**:
   - Stop the deployment pipeline
   - Assess the impact
   - Check application health

2. **Rollback Options**:
   - **Option A**: Restore from backup (if migration failed before completion)
   - **Option B**: Create reverse migration (if migration partially succeeded)
   - **Option C**: Manual SQL fix (if migration is fixable)

3. **Communication**:
   - Notify team immediately
   - Document the issue
   - Create incident report

### Creating Emergency Reverse Migration

If you need to rollback a migration that was applied:

```sql
-- V3__rollback_v2_feature.sql
-- Emergency rollback for V2__add_feature.sql

-- Remove the feature column
ALTER TABLE swap_blotter DROP COLUMN new_feature_column;
GO

-- Remove related indexes
DROP INDEX IF EXISTS idx_new_feature ON swap_blotter;
GO
```

**Important**: 
- Test reverse migration in staging first
- Backup before applying reverse migration
- Document why rollback was necessary

### Health Check Failure

If health checks fail after migration:

1. **Immediate**: Check application logs
2. **Assess**: Determine if issue is migration-related
3. **Decide**: 
   - If fixable → Create hotfix migration
   - If not fixable → Restore from backup
4. **Execute**: Apply chosen solution
5. **Verify**: Confirm application is healthy

## Recommended CI/CD Flow

```
┌─────────────────┐
│  Code Commit    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Build & Test   │
│  (Unit Tests)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Test Migrations │
│ (Test Database) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Deploy Staging  │
│ Backup DB       │
│ Run Migrations  │
│ Integration Test│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Manual Approval │
│ (if risky)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Deploy Prod     │
│ Backup DB       │
│ Run Migrations  │
│ Health Check    │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
  Success  Failure
    │         │
    │         └──► Restore Backup
    │              or Create Reverse Migration
    │
    └──► Monitor & Verify
```

## Summary

Flyway rollback in CI/CD relies on:

1. **Prevention**: Test migrations before production
2. **Backup/Restore**: Automated backups before migrations
3. **Blue-Green**: Keep old version running during deployment
4. **Feature Flags**: Make code changes reversible
5. **Reverse Migrations**: Create new forward migrations to undo changes
6. **Monitoring**: Detect issues and trigger rollback procedures

The key is to design your pipeline to catch issues early and have automated recovery procedures ready.

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Flyway Best Practices](https://flywaydb.org/documentation/learnmore/bestpractices)
- [Database Migration Best Practices](https://www.red-gate.com/simple-talk/databases/database-administration/database-migration-best-practices/)

