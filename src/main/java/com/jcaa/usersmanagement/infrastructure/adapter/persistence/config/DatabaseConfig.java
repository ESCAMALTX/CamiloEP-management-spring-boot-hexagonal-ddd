package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

/**
 * Configuracion de conexion a base de datos.
 *
 * <p>Soporta dos dialectos, seleccionables mediante {@link Dialect}:
 *
 * <ul>
 *   <li><b>MYSQL</b> — {@code jdbc:mysql://host:port/db?sslMode=...}
 *   <li><b>POSTGRESQL</b> — {@code jdbc:postgresql://host:port/db?sslmode=...}
 * </ul>
 *
 * <p>Los parametros de conexion difieren entre motores, por eso el dialecto determina que
 * parametros extra se anaden a la URL.
 */
public record DatabaseConfig(
    String host,
    int port,
    String databaseName,
    String username,
    String password,
    String sslMode,
    Dialect dialect) {

  /** Motores de base de datos soportados. */
  public enum Dialect {
    MYSQL,
    POSTGRESQL
  }

  private static final String MYSQL_URL_TEMPLATE =
      "jdbc:mysql://%s:%d/%s?sslMode=%s&serverTimezone=UTC&allowPublicKeyRetrieval=true";

  /**
   * PostgreSQL usa {@code sslmode} (minusculas, sin camelCase) y no reconoce
   * {@code allowPublicKeyRetrieval} ni {@code serverTimezone}, que son especificos de MySQL.
   *
   * <p>Se anade {@code prepareThreshold=0} para evitar el error "cached plan must not change
   * result type" de PgBouncer, que Supabase usa por defecto en su pooler.
   */
  private static final String POSTGRESQL_URL_TEMPLATE =
      "jdbc:postgresql://%s:%d/%s?sslmode=%s&ApplicationName=users-management"
      + "&prepareThreshold=0";

  public String buildJdbcUrl() {
    return switch (dialect) {
      case MYSQL -> String.format(MYSQL_URL_TEMPLATE, host, port, databaseName, sslMode);
      case POSTGRESQL ->
          String.format(
              POSTGRESQL_URL_TEMPLATE, host, port, databaseName, normalizeSslMode(sslMode));
    };
  }

  /**
   * Traduce los valores de {@code sslMode} de MySQL a los que entiende PostgreSQL.
   *
   * <p>MySQL usa: DISABLED, PREFERRED, REQUIRED, VERIFY_CA, VERIFY_IDENTITY<br>
   * PostgreSQL usa: disable, allow, prefer, require, verify-ca, verify-full
   *
   * @param sslMode valor tal como viene de la configuracion
   * @return el equivalente en PostgreSQL, en minusculas
   */
  private static String normalizeSslMode(final String sslMode) {
    if (sslMode == null || sslMode.isBlank()) {
      return "prefer";
    }
    return switch (sslMode.trim().toUpperCase()) {
      case "DISABLED" -> "disable";
      case "PREFERRED" -> "prefer";
      case "REQUIRED" -> "require";
      case "VERIFY_CA" -> "verify-ca";
      case "VERIFY_IDENTITY" -> "verify-full";
      default -> sslMode.trim().toLowerCase();
    };
  }
}
