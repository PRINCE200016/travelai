package com.tripmind.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUrlEnvironmentTest {

    @Test
    void renderStyleUrl() {
        Map<String, Object> p = DatabaseUrlEnvironment.fromDatabaseUrl(
                "postgres://tripmind:s3cr%40t@dpg-abc123-a.oregon-postgres.render.com/tripmind");
        assertEquals("jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/tripmind?sslmode=require", p.get("spring.datasource.url"));
        assertEquals("tripmind", p.get("spring.datasource.username"));
        assertEquals("s3cr@t", p.get("spring.datasource.password"));
        assertEquals("org.postgresql.Driver", p.get("spring.datasource.driverClassName"));
    }

    @Test
    void internalRenderHostAndExplicitQuery() {
        Map<String, Object> internal = DatabaseUrlEnvironment.fromDatabaseUrl("postgresql://u:p@dpg-abc123-a:5432/db");
        assertEquals("jdbc:postgresql://dpg-abc123-a:5432/db", internal.get("spring.datasource.url"), "internal hostnames need no SSL");

        Map<String, Object> supabase = DatabaseUrlEnvironment.fromDatabaseUrl(
                "postgresql://postgres.ref:pw@aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0");
        assertEquals("jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0",
                supabase.get("spring.datasource.url"));
        assertEquals("postgres.ref", supabase.get("spring.datasource.username"));
    }

    @Test
    void ignoresOtherSchemes() {
        assertTrue(DatabaseUrlEnvironment.fromDatabaseUrl("jdbc:postgresql://h/db").isEmpty());
        assertTrue(DatabaseUrlEnvironment.fromDatabaseUrl("mysql://h/db").isEmpty());
    }
}
