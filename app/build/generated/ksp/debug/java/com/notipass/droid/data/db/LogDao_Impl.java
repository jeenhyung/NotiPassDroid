package com.notipass.droid.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.notipass.droid.model.LogStatus;
import com.notipass.droid.model.NotificationLog;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LogDao_Impl implements LogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NotificationLog> __insertionAdapterOfNotificationLog;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public LogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNotificationLog = new EntityInsertionAdapter<NotificationLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `notification_logs` (`id`,`ruleId`,`ruleName`,`packageName`,`appName`,`title`,`content`,`timestamp`,`status`,`httpCode`,`responseBody`,`errorMessage`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NotificationLog entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getRuleId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getRuleId());
        }
        if (entity.getRuleName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getRuleName());
        }
        statement.bindString(4, entity.getPackageName());
        statement.bindString(5, entity.getAppName());
        statement.bindString(6, entity.getTitle());
        statement.bindString(7, entity.getContent());
        statement.bindLong(8, entity.getTimestamp());
        statement.bindString(9, __LogStatus_enumToString(entity.getStatus()));
        if (entity.getHttpCode() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getHttpCode());
        }
        if (entity.getResponseBody() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getResponseBody());
        }
        if (entity.getErrorMessage() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getErrorMessage());
        }
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM notification_logs";
        return _query;
      }
    };
  }

  @Override
  public Object insertLog(final NotificationLog log, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfNotificationLog.insertAndReturnId(log);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<NotificationLog>> getRecentLogs(final int limit) {
    final String _sql = "SELECT * FROM notification_logs ORDER BY id DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notification_logs"}, new Callable<List<NotificationLog>>() {
      @Override
      @NonNull
      public List<NotificationLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRuleId = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleId");
          final int _cursorIndexOfRuleName = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleName");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfAppName = CursorUtil.getColumnIndexOrThrow(_cursor, "appName");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfHttpCode = CursorUtil.getColumnIndexOrThrow(_cursor, "httpCode");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "responseBody");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final List<NotificationLog> _result = new ArrayList<NotificationLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NotificationLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpRuleId;
            if (_cursor.isNull(_cursorIndexOfRuleId)) {
              _tmpRuleId = null;
            } else {
              _tmpRuleId = _cursor.getLong(_cursorIndexOfRuleId);
            }
            final String _tmpRuleName;
            if (_cursor.isNull(_cursorIndexOfRuleName)) {
              _tmpRuleName = null;
            } else {
              _tmpRuleName = _cursor.getString(_cursorIndexOfRuleName);
            }
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpAppName;
            _tmpAppName = _cursor.getString(_cursorIndexOfAppName);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final LogStatus _tmpStatus;
            _tmpStatus = __LogStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final Integer _tmpHttpCode;
            if (_cursor.isNull(_cursorIndexOfHttpCode)) {
              _tmpHttpCode = null;
            } else {
              _tmpHttpCode = _cursor.getInt(_cursorIndexOfHttpCode);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            _item = new NotificationLog(_tmpId,_tmpRuleId,_tmpRuleName,_tmpPackageName,_tmpAppName,_tmpTitle,_tmpContent,_tmpTimestamp,_tmpStatus,_tmpHttpCode,_tmpResponseBody,_tmpErrorMessage);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getTotalCount() {
    final String _sql = "SELECT COUNT(*) FROM notification_logs";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notification_logs"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getSuccessCount() {
    final String _sql = "SELECT COUNT(*) FROM notification_logs WHERE status = 'SUCCESS'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notification_logs"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getFailedCount() {
    final String _sql = "SELECT COUNT(*) FROM notification_logs WHERE status = 'FAILED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"notification_logs"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __LogStatus_enumToString(@NonNull final LogStatus _value) {
    switch (_value) {
      case SUCCESS: return "SUCCESS";
      case FAILED: return "FAILED";
      case SKIPPED: return "SKIPPED";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private LogStatus __LogStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "SUCCESS": return LogStatus.SUCCESS;
      case "FAILED": return LogStatus.FAILED;
      case "SKIPPED": return LogStatus.SKIPPED;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
