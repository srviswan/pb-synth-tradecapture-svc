# Flyway Database Migrations

This directory contains Flyway migration scripts for database schema management.

## Structure

```
db/migration/
├── V1__initial_schema.sql          # Initial schema migration
├── V2__add_new_feature.sql         # Example: future migrations
└── README.md                       # This file
```

## Naming Convention

Flyway uses a specific naming convention for migration files:

```
V{version}__{description}.sql
```

Examples:
- `V1__initial_schema.sql`
- `V2__add_user_table.sql`
- `V3__add_indexes.sql`

**Important:**
- Version number must be unique and sequential
- Use double underscore `__` to separate version from description
- Description can contain underscores
- File extension must be `.sql`

## Usage

### Automatic Migration

Migrations run automatically on application startup when `spring.flyway.enabled=true` (default).

### Manual Migration Commands

You can also run migrations manually using the Flyway Maven plugin:

```bash
# Apply pending migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Validate applied migrations
mvn flyway:validate

# Generate SQL without applying (dry run)
mvn flyway:migrate -Dflyway.dryRunOutput=/path/to/output.sql

# Repair Flyway metadata table
mvn flyway:repair

# Clean database (WARNING: drops all objects - disabled by default)
# mvn flyway:clean
```

### Creating New Migrations

1. Create a new SQL file following the naming convention: `V{next_version}__{description}.sql`
2. Place it in `src/main/resources/db/migration/`
3. Write your SQL changes
4. On next application startup, Flyway will automatically detect and apply the migration

Example:
```sql
-- Migration: V2__add_audit_columns.sql
ALTER TABLE swap_blotter ADD created_by VARCHAR(100);
ALTER TABLE swap_blotter ADD modified_by VARCHAR(100);
GO
```

## Best Practices

1. **Never modify existing migrations**: Once a migration is applied, never change it. Create a new migration instead.
2. **Idempotent SQL**: Write SQL that can be safely re-run (use `IF NOT EXISTS`, `IF EXISTS` checks)
3. **Test migrations**: Always test migrations on a copy of production data
4. **Version sequentially**: Use sequential version numbers (1, 2, 3, ...)
5. **Descriptive names**: Use clear, descriptive names in migration files
6. **Transaction awareness**: MS SQL Server migrations run in transactions by default
7. **GO statements**: Use `GO` to separate batches for MS SQL Server

## MS SQL Server Specific Notes

- **Partitioning**: Partition functions and schemes must be created before tables
- **Stored Procedures**: Can be included in migrations, use `GO` to separate batches
- **Functions**: Same as stored procedures
- **Schema**: Default schema is `dbo` (configured in application.yml)
- **Batch separators**: Use `GO` statements to separate SQL batches

## Migration History

Flyway automatically creates and maintains the `flyway_schema_history` table to track:
- Which migrations have been applied
- When they were applied
- Checksums for validation
- Execution time

## Configuration

Flyway is configured in `application.yml`:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    schemas: dbo
```

## Troubleshooting

### Migration fails with checksum error
- This means a migration file was modified after being applied
- Use `mvn flyway:repair` to update checksums (only if you know what you're doing)
- Better: Create a new migration instead of modifying existing ones

### Migration order issues
- Ensure version numbers are sequential
- Flyway applies migrations in version order

### MS SQL Server batch issues
- Use `GO` statements to separate batches
- Some statements (like `CREATE FUNCTION`) must be in their own batch

