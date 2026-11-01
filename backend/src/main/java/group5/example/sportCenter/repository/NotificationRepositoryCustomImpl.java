package group5.example.sportCenter.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import group5.example.sportCenter.entity.Notification;

@Repository
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private static final int BATCH_SIZE = 500;
    private static final String INSERT_SQL = """
            INSERT INTO notification (user_id, title, message, is_read, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepositoryCustomImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insertBroadcastNotifications(List<Notification> notifications) {
        int persisted = 0;
        for (int start = 0; start < notifications.size(); start += BATCH_SIZE) {
            int batchStart = start;
            int batchSize = Math.min(BATCH_SIZE, notifications.size() - start);
            int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    Notification notification = notifications.get(batchStart + index);
                    statement.setInt(1, notification.getUser().getId());
                    statement.setString(2, notification.getTitle());
                    statement.setNString(3, notification.getMessage());
                    statement.setBoolean(4, false);
                    statement.setTimestamp(5, Timestamp.valueOf(notification.getCreatedAt()));
                }

                @Override
                public int getBatchSize() {
                    return batchSize;
                }
            });
            if (results.length != batchSize) {
                throw new IllegalStateException("Notification batch did not report every insert result");
            }
            for (int result : results) {
                if (result == 0 || result == Statement.EXECUTE_FAILED) {
                    throw new IllegalStateException("A broadcast notification was not persisted");
                }
                // SUCCESS_NO_INFO still means this one INSERT executed successfully.
                persisted++;
            }
        }
        return persisted;
    }
}
