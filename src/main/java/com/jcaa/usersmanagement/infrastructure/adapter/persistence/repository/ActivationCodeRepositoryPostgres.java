package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import com.jcaa.usersmanagement.application.port.out.ActivateUserPort;
import com.jcaa.usersmanagement.application.port.out.SaveActivationCodePort;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.InvalidActivationCodeException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.mapper.UserPersistenceMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostgreSQL de los codigos de activacion.
 *
 * <p>El codigo se guarda como hash SHA-256. La activacion es atomica: el mismo UPDATE marca el
 * codigo como usado y activa al usuario, de modo que dos intentos simultaneos no puedan reutilizarlo.
 */
@Repository
@Profile("postgres")
@RequiredArgsConstructor
public class ActivationCodeRepositoryPostgres
    implements SaveActivationCodePort, ActivateUserPort {

  private static final String SQL_INSERT =
      "INSERT INTO activation_codes (user_id, code_hash, expires_at) VALUES (?, ?, ?)";

  private static final String SQL_SELECT_BY_HASH =
      "SELECT id, user_id, expires_at, used_at FROM activation_codes "
      + "WHERE code_hash = ? ORDER BY id DESC FETCH FIRST 1 ROW ONLY";

  private static final String SQL_MARK_USED =
      "UPDATE activation_codes SET used_at = CURRENT_TIMESTAMP WHERE id = ? AND used_at IS NULL";

  private static final String SQL_ACTIVATE_USER =
      "UPDATE users SET status = CAST(? AS user_status) WHERE id = ? AND status = CAST(? AS user_status)";

  private static final String SQL_SELECT_USER =
      "SELECT id, name, email, password, role, status, created_at, updated_at "
      + "FROM users WHERE id = ? FETCH FIRST 1 ROW ONLY";

  private final DataSource dataSource;

  @Override
  public void saveCode(final String userId, final String codeHash, final long ttlMinutes) {
    try (final Connection connection = dataSource.getConnection();
         final PreparedStatement statement = connection.prepareStatement(SQL_INSERT)) {
      statement.setString(1, userId);
      statement.setString(2, codeHash);
      statement.setTimestamp(3, Timestamp.from(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)));
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseSaveFailed(userId, exception);
    }
  }

  @Override
  public UserModel activateByCodeHash(final String codeHash) {
    try (final Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        final CodeRow row = findCode(connection, codeHash);
        ensureCodeIsUsable(row);

        try (final PreparedStatement markUsed = connection.prepareStatement(SQL_MARK_USED)) {
          markUsed.setLong(1, row.id());
          if (markUsed.executeUpdate() != 1) {
            throw InvalidActivationCodeException.becauseCodeAlreadyUsed();
          }
        }

        try (final PreparedStatement activate = connection.prepareStatement(SQL_ACTIVATE_USER)) {
          activate.setString(1, UserStatus.ACTIVE.name());
          activate.setString(2, row.userId());
          activate.setString(3, UserStatus.PENDING.name());
          activate.executeUpdate();
        }

        final UserModel user = findUser(connection, row.userId());
        connection.commit();
        return user;
      } catch (final RuntimeException failure) {
        connection.rollback();
        throw failure;
      }
    } catch (final SQLException exception) {
      throw PersistenceException.becauseSaveFailed("activacion", exception);
    }
  }

  private CodeRow findCode(final Connection connection, final String codeHash)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_HASH)) {
      statement.setString(1, codeHash);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw InvalidActivationCodeException.becauseCodeDoesNotExist();
        }
        final Timestamp expiresAt = rs.getTimestamp("expires_at");
        final Timestamp usedAt = rs.getTimestamp("used_at");
        return new CodeRow(
            rs.getLong("id"),
            rs.getString("user_id"),
            expiresAt == null ? null : expiresAt.toInstant(),
            usedAt != null);
      }
    }
  }

  private static void ensureCodeIsUsable(final CodeRow row) {
    if (row.used()) {
      throw InvalidActivationCodeException.becauseCodeAlreadyUsed();
    }
    if (row.expiresAt() == null || row.expiresAt().isBefore(Instant.now())) {
      throw InvalidActivationCodeException.becauseCodeExpired();
    }
  }

  private UserModel findUser(final Connection connection, final String userId)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_USER)) {
      statement.setString(1, userId);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw InvalidActivationCodeException.becauseCodeDoesNotExist();
        }
        return UserPersistenceMapper.fromResultSetToModel(rs);
      }
    }
  }

  private record CodeRow(long id, String userId, Instant expiresAt, boolean used) {}
}
