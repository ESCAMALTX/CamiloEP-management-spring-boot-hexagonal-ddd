package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcaa.usersmanagement.infrastructure.adapter.persistence.config.DatabaseConfig.Dialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for DatabaseConfig.
 *
 * <p>Nota: los tests estan deliberadamente planos (sin {@code @Nested}) porque la configuracion
 * de Surefire del proyecto no ejecuta las clases anidadas.
 */
@DisplayName("DatabaseConfig")
class DatabaseConfigTest {

  private static final String HOST = "db.example.com";
  private static final int PORT = 15425;
  private static final String DATABASE = "crud_usuarios";
  private static final String USERNAME = "avnadmin";
  private static final String PASSWORD = "secret";
  private static final String SSL_MODE = "REQUIRED";

  // ── MySQL ──────────────────────────────────────────────────

  @Test
  @DisplayName("MySQL: construye la URL con sslMode y parametros propios de MySQL")
  void shouldBuildMySqlJdbcUrl() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, SSL_MODE, Dialect.MYSQL);

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertThat(jdbcUrl)
        .isEqualTo(
            "jdbc:mysql://db.example.com:15425/crud_usuarios"
                + "?sslMode=REQUIRED&serverTimezone=UTC&allowPublicKeyRetrieval=true");
  }

  // ── PostgreSQL ─────────────────────────────────────────────

  @Test
  @DisplayName("PostgreSQL: construye la URL con sslmode y sin parametros exclusivos de MySQL")
  void shouldBuildPostgresUrl() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, SSL_MODE, Dialect.POSTGRESQL);

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertThat(jdbcUrl)
        .startsWith("jdbc:postgresql://db.example.com:15425/crud_usuarios")
        .contains("sslmode=require")
        .doesNotContain("allowPublicKeyRetrieval")
        .doesNotContain("serverTimezone");
  }

  @Test
  @DisplayName("PostgreSQL: traduce los valores de sslMode de MySQL a los de PostgreSQL")
  void shouldTranslateSslModeValues() {
    // Act & Assert
    assertThat(urlWithSslMode("DISABLED")).contains("sslmode=disable");
    assertThat(urlWithSslMode("PREFERRED")).contains("sslmode=prefer");
    assertThat(urlWithSslMode("REQUIRED")).contains("sslmode=require");
    assertThat(urlWithSslMode("VERIFY_CA")).contains("sslmode=verify-ca");
    assertThat(urlWithSslMode("VERIFY_IDENTITY")).contains("sslmode=verify-full");
  }

  @Test
  @DisplayName("PostgreSQL: usa sslmode=prefer cuando el valor viene vacio o nulo")
  void shouldDefaultToPreferWhenSslModeIsBlank() {
    // Act & Assert
    assertThat(urlWithSslMode("")).contains("sslmode=prefer");
    assertThat(urlWithSslMode(null)).contains("sslmode=prefer");
  }

  @Test
  @DisplayName("PostgreSQL: acepta valores ya en formato PostgreSQL sin romperlos")
  void shouldPassThroughAlreadyNormalizedValues() {
    // Act & Assert
    assertThat(urlWithSslMode("verify-full")).contains("sslmode=verify-full");
  }

  private static String urlWithSslMode(final String sslMode) {
    return new DatabaseConfig(
            HOST, PORT, DATABASE, USERNAME, PASSWORD, sslMode, Dialect.POSTGRESQL)
        .buildJdbcUrl();
  }
}
