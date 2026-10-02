package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for UserRepositoryPostgres.
 *
 * <p>Replica la cobertura de {@link UserRepositoryMySQLTest} sobre el adaptador PostgreSQL, y
 * verifica ademas las diferencias de dialecto:
 *
 * <ul>
 *   <li>El INSERT usa CURRENT_TIMESTAMP en lugar de NOW().
 *   <li>El UPDATE no toca updated_at (lo gestiona un trigger en la base de datos).
 *   <li>Los SELECT usan FETCH FIRST 1 ROW ONLY en lugar de LIMIT 1.
 * </ul>
 *
 * <p>Nota: los tests estan deliberadamente planos (sin {@code @Nested}) porque la configuracion
 * de Surefire del proyecto no ejecuta las clases anidadas.
 */
@DisplayName("UserRepositoryPostgres")
@ExtendWith(MockitoExtension.class)
class UserRepositoryPostgresTest {

  private static final String ID = "u-001";
  private static final String NAME = "John Doe";
  private static final String EMAIL = "john@example.com";
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstuO";
  private static final String ROLE = "ADMIN";
  private static final String STATUS = "ACTIVE";
  private static final String CREATED_AT = "2024-01-01";
  private static final String UPDATED_AT = "2024-01-02";

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private ResultSet resultSet;

  private UserRepositoryPostgres repository;
  private UserModel userModel;
  private UserId userId;
  private UserEmail userEmail;

  @BeforeEach
  void setUp() {
    repository = new UserRepositoryPostgres(dataSource);
    userId = new UserId(ID);
    userEmail = new UserEmail(EMAIL);
    userModel =
        new UserModel(
            userId,
            new UserName(NAME),
            userEmail,
            UserPassword.fromHash(HASH),
            UserRole.ADMIN,
            UserStatus.ACTIVE);
  }

  private void configureStatementAndResultSet() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
  }

  private void configureResultSetRow() throws SQLException {
    when(resultSet.getString("id")).thenReturn(ID);
    when(resultSet.getString("name")).thenReturn(NAME);
    when(resultSet.getString("email")).thenReturn(EMAIL);
    when(resultSet.getString("password")).thenReturn(HASH);
    when(resultSet.getString("role")).thenReturn(ROLE);
    when(resultSet.getString("status")).thenReturn(STATUS);
    when(resultSet.getString("created_at")).thenReturn(CREATED_AT);
    when(resultSet.getString("updated_at")).thenReturn(UPDATED_AT);
  }

  /** Extrae la sentencia SQL capturada que contiene el fragmento indicado. */
  private String captureSqlContaining(final String fragment) throws SQLException {
    final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection, atLeastOnce()).prepareStatement(sqlCaptor.capture());
    return sqlCaptor.getAllValues().stream()
        .filter(sql -> sql.contains(fragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No se encontro SQL con: " + fragment));
  }

  // ══════════════════════════════════════════════════════════
  // save()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("save() ejecuta el INSERT y devuelve el usuario persistido consultado por id")
  void shouldSaveUserAndReturnById() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    final UserModel result = repository.save(userModel);

    // Assert
    assertAll(
        "save() happy path",
        () -> assertEquals(ID, result.getId().value(), "id"),
        () -> assertEquals(NAME, result.getName().value(), "name"),
        () -> assertEquals(EMAIL, result.getEmail().value(), "email"));
  }

  @Test
  @DisplayName("save() lanza PersistenceException cuando el INSERT falla")
  void shouldThrowPersistenceExceptionWhenInsertFails() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeUpdate()).thenThrow(new SQLException("duplicate key"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.save(userModel));
  }

  @Test
  @DisplayName("save() lanza UserNotFoundException si el usuario no aparece tras el INSERT")
  void shouldThrowUserNotFoundWhenInsertThenFindReturnsEmpty() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(false);

    // Act & Assert
    assertThrows(UserNotFoundException.class, () -> repository.save(userModel));
  }

  @Test
  @DisplayName("save() usa CURRENT_TIMESTAMP en el INSERT, no NOW() (dialecto PostgreSQL)")
  void shouldUseCurrentTimestampInsteadOfNow() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    repository.save(userModel);

    // Assert
    final String insertSql = captureSqlContaining("INSERT INTO users");
    assertAll(
        "dialecto del INSERT",
        () -> assertTrue(insertSql.contains("CURRENT_TIMESTAMP"), "debe usar CURRENT_TIMESTAMP"),
        () -> assertFalse(insertSql.contains("NOW()"), "no debe usar NOW() de MySQL"));
  }

  // ══════════════════════════════════════════════════════════
  // update()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("update() ejecuta el UPDATE y devuelve el usuario actualizado")
  void shouldUpdateUserAndReturnById() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    final UserModel result = repository.update(userModel);

    // Assert
    assertEquals(ID, result.getId().value());
    verify(statement, atLeastOnce()).executeUpdate();
  }

  @Test
  @DisplayName("update() lanza PersistenceException cuando el UPDATE falla")
  void shouldThrowPersistenceExceptionWhenUpdateFails() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeUpdate()).thenThrow(new SQLException("constraint violation"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.update(userModel));
  }

  @Test
  @DisplayName("update() no asigna updated_at: lo gestiona el trigger de la base de datos")
  void shouldNotSetUpdatedAtInUpdateStatement() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    repository.update(userModel);

    // Assert
    final String updateSql = captureSqlContaining("UPDATE users");
    assertAll(
        "dialecto del UPDATE",
        () -> assertFalse(updateSql.contains("updated_at"), "el trigger gestiona updated_at"),
        () -> assertFalse(updateSql.contains("NOW()"), "no debe usar NOW() de MySQL"));
  }

  // ══════════════════════════════════════════════════════════
  // getById()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("getById() devuelve el usuario cuando existe")
  void shouldReturnUserWhenFoundById() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    final var result = repository.getById(userId);

    // Assert
    assertAll(
        "getById() encontrado",
        () -> assertTrue(result.isPresent()),
        () -> assertEquals(ID, result.orElseThrow().getId().value()));
  }

  @Test
  @DisplayName("getById() devuelve Optional vacio cuando no existe")
  void shouldReturnEmptyWhenNotFoundById() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(false);

    // Act & Assert
    assertTrue(repository.getById(userId).isEmpty());
  }

  @Test
  @DisplayName("getById() lanza PersistenceException ante un SQLException")
  void shouldThrowPersistenceExceptionOnGetByIdSqlError() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenThrow(new SQLException("connection lost"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.getById(userId));
  }

  @Test
  @DisplayName("getById() usa FETCH FIRST 1 ROW ONLY (estandar SQL), no LIMIT 1")
  void shouldUseFetchFirstInsteadOfLimit() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(false);

    // Act
    repository.getById(userId);

    // Assert
    final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    final String selectSql = sqlCaptor.getValue();
    assertAll(
        "dialecto del SELECT",
        () -> assertTrue(selectSql.contains("FETCH FIRST 1 ROW ONLY"), "debe usar FETCH FIRST"),
        () -> assertFalse(selectSql.contains("LIMIT 1"), "no debe usar LIMIT de MySQL"));
  }

  // ══════════════════════════════════════════════════════════
  // getByEmail()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("getByEmail() devuelve el usuario cuando el email existe")
  void shouldReturnUserWhenFoundByEmail() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(true);
    configureResultSetRow();

    // Act
    final var result = repository.getByEmail(userEmail);

    // Assert
    assertAll(
        "getByEmail() encontrado",
        () -> assertTrue(result.isPresent()),
        () -> assertEquals(EMAIL, result.orElseThrow().getEmail().value()));
  }

  @Test
  @DisplayName("getByEmail() devuelve Optional vacio cuando el email no existe")
  void shouldReturnEmptyWhenNotFoundByEmail() throws SQLException {
    // Arrange
    configureStatementAndResultSet();
    when(resultSet.next()).thenReturn(false);

    // Act & Assert
    assertTrue(repository.getByEmail(userEmail).isEmpty());
  }

  @Test
  @DisplayName("getByEmail() lanza PersistenceException ante un SQLException")
  void shouldThrowPersistenceExceptionOnGetByEmailSqlError() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenThrow(new SQLException("timeout"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.getByEmail(userEmail));
  }

  // ══════════════════════════════════════════════════════════
  // getAll()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("getAll() devuelve la lista de usuarios")
  void shouldReturnAllUsers() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    // El mapper itera mientras next() sea true: dos filas y fin.
    when(resultSet.next()).thenReturn(true, true, false);
    configureResultSetRow();

    // Act
    final List<UserModel> result = repository.getAll();

    // Assert
    assertEquals(2, result.size());
  }

  @Test
  @DisplayName("getAll() devuelve lista vacia cuando no hay usuarios")
  void shouldReturnEmptyListWhenNoUsers() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    // Act & Assert
    assertTrue(repository.getAll().isEmpty());
  }

  @Test
  @DisplayName("getAll() lanza PersistenceException ante un SQLException")
  void shouldThrowPersistenceExceptionOnGetAllSqlError() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.getAll());
  }

  // ══════════════════════════════════════════════════════════
  // delete()
  // ══════════════════════════════════════════════════════════

  @Test
  @DisplayName("delete() ejecuta el DELETE correctamente")
  void shouldDeleteUser() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);

    // Act
    repository.delete(userId);

    // Assert
    verify(statement).setString(1, ID);
    verify(statement).executeUpdate();
  }

  @Test
  @DisplayName("delete() lanza PersistenceException ante un SQLException")
  void shouldThrowPersistenceExceptionOnDeleteSqlError() throws SQLException {
    // Arrange
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeUpdate()).thenThrow(new SQLException("fk violation"));

    // Act & Assert
    assertThrows(PersistenceException.class, () -> repository.delete(userId));
  }
}
