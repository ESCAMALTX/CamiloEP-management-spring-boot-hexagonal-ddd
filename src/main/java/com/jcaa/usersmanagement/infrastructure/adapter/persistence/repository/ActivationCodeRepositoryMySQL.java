package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import com.jcaa.usersmanagement.application.port.out.ActivateUserPort;
import com.jcaa.usersmanagement.application.port.out.SaveActivationCodePort;
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
 * Adaptador MySQL de los codigos de activacion (mismo contrato que la version PostgreSQL).
 */
@Repository
@Profile("mysql")
@RequiredArgsConstructor
public class ActivationCodeRepositoryMySQL
    implements SaveActivationCodePort, ActivateUserPort {

  private static final String SQL_INSERT =
      "INSERT INTO activation_codes (user_id, code_hash, expires_at) VALUES (?, ?, ?)";

  private static final String SQL_SELECT_BY_HASH =
      "SELECT id, user_id, expires_at, used_at FROM activation_codes "
      + "WHERE code_hash = ? ORDER BY id DESC LIMIT 1";

  private static final String SQL_MARK_USED =
      "UPDATE activation_codes SET used_at = CURRENT_TIMESTAMP WHERE id = ? AND used_at IS NULL";

  private static final String SQL_ACTIVATE_USER =
      "UPDATE users SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'";

  private static final String SQL_SELECT_USER =
      "SELECT id, name, email, password, role, status, created_at, updated_at "
      + "FROM users WHERE id = ? LIMIT 1";

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
      final boolean previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        final Long codeId = findUsableCodeId(connection, codeHash);

        try (final PreparedStatement markUsed = connection.prepareStatement(SQL_MARK_USED)) {
          markUsed.setLong(1, codeId);
          if (markUsed.executeUpdate() != 1) {
            throw InvalidActivationCodeException.becauseCodeAlreadyUsed();
          }
        }

        final String userId = findUserId(connection, codeId);
        try (final PreparedStatement activate = connection.prepareStatement(SQL_ACTIVATE_USER)) {
          activate.setString(1, userId);
          activate.executeUpdate();
        }

        final UserModel user = findUser(connection, userId);
        connection.commit();
        connection.setAutoCommit(previousAutoCommit);
        return user;
      } catch (final RuntimeException failure) {
        connection.rollback();
        connection.setAutoCommit(previousAutoCommit);
        throw failure;
      }
    } catch (final SQLException exception) {
      throw PersistenceException.becauseSaveFailed("activacion", exception);
    }
  }

  private Long findUsableCodeId(final Connection connection, final String codeHash)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_HASH)) {
      statement.setString(1, codeHash);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw InvalidActivationCodeException.becauseCodeDoesNotExist();
        }
        final Timestamp expiresAt = rs.getTimestamp("expires_at");
        if (rs.getTimestamp("used_at") != null) {
          throw InvalidActivationCodeException.becauseCodeAlreadyUsed();
        }
        if (expiresAt == null || expiresAt.toInstant().isBefore(Instant.now())) {
          throw InvalidActivationCodeException.becauseCodeExpired();
        }
        return rs.getLong("id");
      }
    }
  }

  private String findUserId(final Connection connection, final long codeId) throws SQLException {
    try (final PreparedStatement statement =
             connection.prepareStatement("SELECT user_id FROM activation_codes WHERE id = ?")) {
      statement.setLong(1, codeId);
      try (final ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw InvalidActivationCodeException.becauseCodeDoesNotExist();
        }
        return rs.getString("user_id");
      }
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
}
