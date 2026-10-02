package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.infrastructure.adapter.persistence.config.DatabaseConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.config.DatabaseConfig.Dialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class DataSourceSpringConfig {

  private static final String PROP_DB_HOST     = "${db.host}";
  private static final String PROP_DB_PORT     = "${db.port}";
  private static final String PROP_DB_NAME     = "${db.name}";
  private static final String PROP_DB_USERNAME = "${db.username}";
  private static final String PROP_DB_PASSWORD = "${db.password}";
  private static final String PROP_DB_SSL_MODE = "${db.ssl-mode}";

  private static final String PROFILE_POSTGRES = "postgres";

  private static final String LOG_DATASOURCE_INIT =
      "[DataSourceSpringConfig] DataSource inicializado. dialect={} host={} port={}";

  @Value(PROP_DB_HOST)
  private String dbHost;

  @Value(PROP_DB_PORT)
  private int dbPort;

  @Value(PROP_DB_NAME)
  private String dbName;

  @Value(PROP_DB_USERNAME)
  private String dbUsername;

  @Value(PROP_DB_PASSWORD)
  private String dbPassword;

  @Value(PROP_DB_SSL_MODE)
  private String dbSslMode;

  private final Environment environment;

  public DataSourceSpringConfig(final Environment environment) {
    this.environment = environment;
  }

  @Bean
  public DataSource dataSource() {
    final Dialect dialect = resolveDialect();

    final DatabaseConfig config =
        new DatabaseConfig(dbHost, dbPort, dbName, dbUsername, dbPassword, dbSslMode, dialect);

    final HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.buildJdbcUrl());
    hikariConfig.setUsername(config.username());
    hikariConfig.setPassword(config.password());
    hikariConfig.setMaximumPoolSize(10);
    hikariConfig.setMinimumIdle(2);
    hikariConfig.setConnectionTimeout(30_000);

    log.info(LOG_DATASOURCE_INIT, dialect, dbHost, dbPort);
    return new HikariDataSource(hikariConfig);
  }

  /**
   * Determina el dialecto segun los perfiles activos.
   *
   * <p>El perfil {@code postgres} selecciona PostgreSQL; cualquier otro caso usa MySQL, que es el
   * comportamiento historico de la aplicacion.
   */
  private Dialect resolveDialect() {
    for (final String profile : environment.getActiveProfiles()) {
      if (PROFILE_POSTGRES.equals(profile)) {
        return Dialect.POSTGRESQL;
      }
    }
    return Dialect.MYSQL;
  }
}

