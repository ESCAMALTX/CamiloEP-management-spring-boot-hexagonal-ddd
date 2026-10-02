package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import com.jcaa.usersmanagement.application.port.out.DeleteUserPort;
import com.jcaa.usersmanagement.application.port.out.GetAllUsersPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByEmailPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.SaveUserPort;
import com.jcaa.usersmanagement.application.port.out.UpdateUserPort;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.dto.UserPersistenceDto;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.mapper.UserPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia para PostgreSQL.
 *
 * <p>Implementa los mismos puertos de salida que {@link UserRepositoryMySQL}, pero con SQL
 * adaptado al dialecto de PostgreSQL. Es un adaptador alternativo, no un reemplazo: se
 * selecciona mediante el perfil de Spring.
 *
 * <p><b>Diferencias de dialecto respecto a MySQL:</b>
 *
 * <ul>
 *   <li>{@code NOW()} se reemplaza por {@code CURRENT_TIMESTAMP} (estandar SQL).
 *   <li>{@code LIMIT 1} se mantiene, pero se elimina el uso de {@code ORDER BY} implicito.
 *   <li>La actualizacion automatica de {@code updated_at} la realiza un TRIGGER en la base de
 *       datos (ver {@code schema-postgres.sql}), por lo que la sentencia UPDATE no lo toca.
 *   <li>{@code role} y {@code status} son tipos ENUM nativos de PostgreSQL; se leen con {@code
 *       getString} igual que en MySQL.
 * </ul>
 *
 * <p><b>Nota sobre {@code SELECT ... LIMIT 1}:</b> en PostgreSQL {@code LIMIT} es valido, pero se
 * prefiere {@code FETCH FIRST 1 ROW ONLY} por ser el estandar SQL:2008. Se usa esta ultima forma
 * para maximizar la portabilidad.
 *
 * @see UserRepositoryMySQL
 */
@Slf4j
@Repository
@Profile("postgres")
@RequiredArgsConstructor
public class UserRepositoryPostgres
    implements SaveUserPort,
        UpdateUserPort,
        GetUserByIdPort,
        GetUserByEmailPort,
        GetAllUsersPort,
        DeleteUserPort {

  private static final String SQL_INSERT =
      "INSERT INTO users "
      + "(id, name, email, password, role, status, created_at, updated_at) "
      + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

  // updated_at NO se asigna aqui: lo actualiza el trigger users_set_updated_at.
  private static final String SQL_UPDATE =
      "UPDATE users SET name = ?, email = ?, password = ?, role = ?, status = ? "
      + "WHERE id = ?";

  private static final String SQL_SELECT_BY_ID =
      "SELECT id, name, email, password, role, status, created_at, updated_at "
      + "FROM users "
      + "WHERE id = ? FETCH FIRST 1 ROW ONLY";

  private static final String SQL_SELECT_BY_EMAIL =
      "SELECT id, name, email, password, role, status, created_at, updated_at "
      + "FROM users "
      + "WHERE email = ? FETCH FIRST 1 ROW ONLY";

  private static final String SQL_SELECT_ALL =
      "SELECT id, name, email, password, role, status, created_at, updated_at "
      + "FROM users "
      + "ORDER BY name ASC";

  private static final String SQL_DELETE =
      "DELETE FROM users "
      + "WHERE id = ?";

  private final DataSource dataSource;

  @Override
  public UserModel save(final UserModel user) {
    final UserPersistenceDto dto = UserPersistenceMapper.fromModelToDto(user);
    executeSave(dto);
    return findByIdOrFail(user.getId());
  }

  @Override
  public UserModel update(final UserModel user) {
    final UserPersistenceDto dto = UserPersistenceMapper.fromModelToDto(user);
    executeUpdate(dto);
    return findByIdOrFail(user.getId());
  }

  @Override
  public Optional<UserModel> getById(final UserId userId) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_ID)) {
      statement.setString(1, userId.value());
      final ResultSet resultSet = statement.executeQuery();
      if (!resultSet.next()) {
        return Optional.empty();
      }
      return Optional.of(UserPersistenceMapper.fromResultSetToModel(resultSet));
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFindByIdFailed(userId.value(), exception);
    }
  }

  @Override
  public Optional<UserModel> getByEmail(final UserEmail email) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_EMAIL)) {
      statement.setString(1, email.value());
      final ResultSet resultSet = statement.executeQuery();
      if (!resultSet.next()) {
        return Optional.empty();
      }
      return Optional.of(UserPersistenceMapper.fromResultSetToModel(resultSet));
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFindByEmailFailed(email.value(), exception);
    }
  }

  @Override
  public List<UserModel> getAll() {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_ALL)) {
      final ResultSet resultSet = statement.executeQuery();
      return UserPersistenceMapper.fromResultSetToModelList(resultSet);
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFindAllFailed(exception);
    }
  }

  @Override
  public void delete(final UserId userId) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_DELETE)) {
      statement.setString(1, userId.value());
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseDeleteFailed(userId.value(), exception);
    }
  }

  private void executeSave(final UserPersistenceDto dto) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_INSERT)) {
      statement.setString(1, dto.id());
      statement.setString(2, dto.name());
      statement.setString(3, dto.email());
      statement.setString(4, dto.password());
      statement.setString(5, dto.role());
      statement.setString(6, dto.status());
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseSaveFailed(dto.id(), exception);
    }
  }

  private void executeUpdate(final UserPersistenceDto dto) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_UPDATE)) {
      statement.setString(1, dto.name());
      statement.setString(2, dto.email());
      statement.setString(3, dto.password());
      statement.setString(4, dto.role());
      statement.setString(5, dto.status());
      statement.setString(6, dto.id());
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseUpdateFailed(dto.id(), exception);
    }
  }

  private UserModel findByIdOrFail(final UserId userId) {
    return getById(userId)
        .orElseThrow(() -> UserNotFoundException.becauseIdWasNotFound(userId.value()));
  }
}
