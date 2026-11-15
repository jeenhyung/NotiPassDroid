package com.notipass.droid.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.notipass.droid.model.MatchMode;
import com.notipass.droid.model.Rule;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
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
public final class RuleDao_Impl implements RuleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Rule> __insertionAdapterOfRule;

  private final EntityDeletionOrUpdateAdapter<Rule> __deletionAdapterOfRule;

  private final EntityDeletionOrUpdateAdapter<Rule> __updateAdapterOfRule;

  private final SharedSQLiteStatement __preparedStmtOfSetRuleEnabled;

  public RuleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRule = new EntityInsertionAdapter<Rule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `rules` (`id`,`name`,`isEnabled`,`targetPackage`,`targetAppName`,`titleMatchMode`,`titleKeyword`,`contentMatchMode`,`contentKeyword`,`webhookUrl`,`webhookMethod`,`webhookHeadersJson`,`webhookBodyTemplate`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Rule entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final int _tmp = entity.isEnabled() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getTargetPackage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTargetPackage());
        }
        if (entity.getTargetAppName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTargetAppName());
        }
        statement.bindString(6, __MatchMode_enumToString(entity.getTitleMatchMode()));
        statement.bindString(7, entity.getTitleKeyword());
        statement.bindString(8, __MatchMode_enumToString(entity.getContentMatchMode()));
        statement.bindString(9, entity.getContentKeyword());
        statement.bindString(10, entity.getWebhookUrl());
        statement.bindString(11, entity.getWebhookMethod());
        statement.bindString(12, entity.getWebhookHeadersJson());
        statement.bindString(13, entity.getWebhookBodyTemplate());
        statement.bindLong(14, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfRule = new EntityDeletionOrUpdateAdapter<Rule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `rules` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Rule entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRule = new EntityDeletionOrUpdateAdapter<Rule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `rules` SET `id` = ?,`name` = ?,`isEnabled` = ?,`targetPackage` = ?,`targetAppName` = ?,`titleMatchMode` = ?,`titleKeyword` = ?,`contentMatchMode` = ?,`contentKeyword` = ?,`webhookUrl` = ?,`webhookMethod` = ?,`webhookHeadersJson` = ?,`webhookBodyTemplate` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Rule entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final int _tmp = entity.isEnabled() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getTargetPackage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTargetPackage());
        }
        if (entity.getTargetAppName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTargetAppName());
        }
        statement.bindString(6, __MatchMode_enumToString(entity.getTitleMatchMode()));
        statement.bindString(7, entity.getTitleKeyword());
        statement.bindString(8, __MatchMode_enumToString(entity.getContentMatchMode()));
        statement.bindString(9, entity.getContentKeyword());
        statement.bindString(10, entity.getWebhookUrl());
        statement.bindString(11, entity.getWebhookMethod());
        statement.bindString(12, entity.getWebhookHeadersJson());
        statement.bindString(13, entity.getWebhookBodyTemplate());
        statement.bindLong(14, entity.getCreatedAt());
        statement.bindLong(15, entity.getId());
      }
    };
    this.__preparedStmtOfSetRuleEnabled = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE rules SET isEnabled = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertRule(final Rule rule, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfRule.insertAndReturnId(rule);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteRule(final Rule rule, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRule.handle(rule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateRule(final Rule rule, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfRule.handle(rule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setRuleEnabled(final long id, final boolean isEnabled,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetRuleEnabled.acquire();
        int _argIndex = 1;
        final int _tmp = isEnabled ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfSetRuleEnabled.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Rule>> getAllRules() {
    final String _sql = "SELECT * FROM rules ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"rules"}, new Callable<List<Rule>>() {
      @Override
      @NonNull
      public List<Rule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isEnabled");
          final int _cursorIndexOfTargetPackage = CursorUtil.getColumnIndexOrThrow(_cursor, "targetPackage");
          final int _cursorIndexOfTargetAppName = CursorUtil.getColumnIndexOrThrow(_cursor, "targetAppName");
          final int _cursorIndexOfTitleMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "titleMatchMode");
          final int _cursorIndexOfTitleKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "titleKeyword");
          final int _cursorIndexOfContentMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "contentMatchMode");
          final int _cursorIndexOfContentKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "contentKeyword");
          final int _cursorIndexOfWebhookUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookUrl");
          final int _cursorIndexOfWebhookMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookMethod");
          final int _cursorIndexOfWebhookHeadersJson = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookHeadersJson");
          final int _cursorIndexOfWebhookBodyTemplate = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookBodyTemplate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Rule> _result = new ArrayList<Rule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Rule _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsEnabled);
            _tmpIsEnabled = _tmp != 0;
            final String _tmpTargetPackage;
            if (_cursor.isNull(_cursorIndexOfTargetPackage)) {
              _tmpTargetPackage = null;
            } else {
              _tmpTargetPackage = _cursor.getString(_cursorIndexOfTargetPackage);
            }
            final String _tmpTargetAppName;
            if (_cursor.isNull(_cursorIndexOfTargetAppName)) {
              _tmpTargetAppName = null;
            } else {
              _tmpTargetAppName = _cursor.getString(_cursorIndexOfTargetAppName);
            }
            final MatchMode _tmpTitleMatchMode;
            _tmpTitleMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfTitleMatchMode));
            final String _tmpTitleKeyword;
            _tmpTitleKeyword = _cursor.getString(_cursorIndexOfTitleKeyword);
            final MatchMode _tmpContentMatchMode;
            _tmpContentMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfContentMatchMode));
            final String _tmpContentKeyword;
            _tmpContentKeyword = _cursor.getString(_cursorIndexOfContentKeyword);
            final String _tmpWebhookUrl;
            _tmpWebhookUrl = _cursor.getString(_cursorIndexOfWebhookUrl);
            final String _tmpWebhookMethod;
            _tmpWebhookMethod = _cursor.getString(_cursorIndexOfWebhookMethod);
            final String _tmpWebhookHeadersJson;
            _tmpWebhookHeadersJson = _cursor.getString(_cursorIndexOfWebhookHeadersJson);
            final String _tmpWebhookBodyTemplate;
            _tmpWebhookBodyTemplate = _cursor.getString(_cursorIndexOfWebhookBodyTemplate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Rule(_tmpId,_tmpName,_tmpIsEnabled,_tmpTargetPackage,_tmpTargetAppName,_tmpTitleMatchMode,_tmpTitleKeyword,_tmpContentMatchMode,_tmpContentKeyword,_tmpWebhookUrl,_tmpWebhookMethod,_tmpWebhookHeadersJson,_tmpWebhookBodyTemplate,_tmpCreatedAt);
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
  public Object getEnabledRules(final Continuation<? super List<Rule>> $completion) {
    final String _sql = "SELECT * FROM rules WHERE isEnabled = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Rule>>() {
      @Override
      @NonNull
      public List<Rule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isEnabled");
          final int _cursorIndexOfTargetPackage = CursorUtil.getColumnIndexOrThrow(_cursor, "targetPackage");
          final int _cursorIndexOfTargetAppName = CursorUtil.getColumnIndexOrThrow(_cursor, "targetAppName");
          final int _cursorIndexOfTitleMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "titleMatchMode");
          final int _cursorIndexOfTitleKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "titleKeyword");
          final int _cursorIndexOfContentMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "contentMatchMode");
          final int _cursorIndexOfContentKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "contentKeyword");
          final int _cursorIndexOfWebhookUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookUrl");
          final int _cursorIndexOfWebhookMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookMethod");
          final int _cursorIndexOfWebhookHeadersJson = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookHeadersJson");
          final int _cursorIndexOfWebhookBodyTemplate = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookBodyTemplate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Rule> _result = new ArrayList<Rule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Rule _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsEnabled);
            _tmpIsEnabled = _tmp != 0;
            final String _tmpTargetPackage;
            if (_cursor.isNull(_cursorIndexOfTargetPackage)) {
              _tmpTargetPackage = null;
            } else {
              _tmpTargetPackage = _cursor.getString(_cursorIndexOfTargetPackage);
            }
            final String _tmpTargetAppName;
            if (_cursor.isNull(_cursorIndexOfTargetAppName)) {
              _tmpTargetAppName = null;
            } else {
              _tmpTargetAppName = _cursor.getString(_cursorIndexOfTargetAppName);
            }
            final MatchMode _tmpTitleMatchMode;
            _tmpTitleMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfTitleMatchMode));
            final String _tmpTitleKeyword;
            _tmpTitleKeyword = _cursor.getString(_cursorIndexOfTitleKeyword);
            final MatchMode _tmpContentMatchMode;
            _tmpContentMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfContentMatchMode));
            final String _tmpContentKeyword;
            _tmpContentKeyword = _cursor.getString(_cursorIndexOfContentKeyword);
            final String _tmpWebhookUrl;
            _tmpWebhookUrl = _cursor.getString(_cursorIndexOfWebhookUrl);
            final String _tmpWebhookMethod;
            _tmpWebhookMethod = _cursor.getString(_cursorIndexOfWebhookMethod);
            final String _tmpWebhookHeadersJson;
            _tmpWebhookHeadersJson = _cursor.getString(_cursorIndexOfWebhookHeadersJson);
            final String _tmpWebhookBodyTemplate;
            _tmpWebhookBodyTemplate = _cursor.getString(_cursorIndexOfWebhookBodyTemplate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Rule(_tmpId,_tmpName,_tmpIsEnabled,_tmpTargetPackage,_tmpTargetAppName,_tmpTitleMatchMode,_tmpTitleKeyword,_tmpContentMatchMode,_tmpContentKeyword,_tmpWebhookUrl,_tmpWebhookMethod,_tmpWebhookHeadersJson,_tmpWebhookBodyTemplate,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRuleById(final long id, final Continuation<? super Rule> $completion) {
    final String _sql = "SELECT * FROM rules WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Rule>() {
      @Override
      @Nullable
      public Rule call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfIsEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isEnabled");
          final int _cursorIndexOfTargetPackage = CursorUtil.getColumnIndexOrThrow(_cursor, "targetPackage");
          final int _cursorIndexOfTargetAppName = CursorUtil.getColumnIndexOrThrow(_cursor, "targetAppName");
          final int _cursorIndexOfTitleMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "titleMatchMode");
          final int _cursorIndexOfTitleKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "titleKeyword");
          final int _cursorIndexOfContentMatchMode = CursorUtil.getColumnIndexOrThrow(_cursor, "contentMatchMode");
          final int _cursorIndexOfContentKeyword = CursorUtil.getColumnIndexOrThrow(_cursor, "contentKeyword");
          final int _cursorIndexOfWebhookUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookUrl");
          final int _cursorIndexOfWebhookMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookMethod");
          final int _cursorIndexOfWebhookHeadersJson = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookHeadersJson");
          final int _cursorIndexOfWebhookBodyTemplate = CursorUtil.getColumnIndexOrThrow(_cursor, "webhookBodyTemplate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final Rule _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final boolean _tmpIsEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsEnabled);
            _tmpIsEnabled = _tmp != 0;
            final String _tmpTargetPackage;
            if (_cursor.isNull(_cursorIndexOfTargetPackage)) {
              _tmpTargetPackage = null;
            } else {
              _tmpTargetPackage = _cursor.getString(_cursorIndexOfTargetPackage);
            }
            final String _tmpTargetAppName;
            if (_cursor.isNull(_cursorIndexOfTargetAppName)) {
              _tmpTargetAppName = null;
            } else {
              _tmpTargetAppName = _cursor.getString(_cursorIndexOfTargetAppName);
            }
            final MatchMode _tmpTitleMatchMode;
            _tmpTitleMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfTitleMatchMode));
            final String _tmpTitleKeyword;
            _tmpTitleKeyword = _cursor.getString(_cursorIndexOfTitleKeyword);
            final MatchMode _tmpContentMatchMode;
            _tmpContentMatchMode = __MatchMode_stringToEnum(_cursor.getString(_cursorIndexOfContentMatchMode));
            final String _tmpContentKeyword;
            _tmpContentKeyword = _cursor.getString(_cursorIndexOfContentKeyword);
            final String _tmpWebhookUrl;
            _tmpWebhookUrl = _cursor.getString(_cursorIndexOfWebhookUrl);
            final String _tmpWebhookMethod;
            _tmpWebhookMethod = _cursor.getString(_cursorIndexOfWebhookMethod);
            final String _tmpWebhookHeadersJson;
            _tmpWebhookHeadersJson = _cursor.getString(_cursorIndexOfWebhookHeadersJson);
            final String _tmpWebhookBodyTemplate;
            _tmpWebhookBodyTemplate = _cursor.getString(_cursorIndexOfWebhookBodyTemplate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new Rule(_tmpId,_tmpName,_tmpIsEnabled,_tmpTargetPackage,_tmpTargetAppName,_tmpTitleMatchMode,_tmpTitleKeyword,_tmpContentMatchMode,_tmpContentKeyword,_tmpWebhookUrl,_tmpWebhookMethod,_tmpWebhookHeadersJson,_tmpWebhookBodyTemplate,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __MatchMode_enumToString(@NonNull final MatchMode _value) {
    switch (_value) {
      case IGNORE: return "IGNORE";
      case CONTAINS: return "CONTAINS";
      case EXACT: return "EXACT";
      case REGEX: return "REGEX";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private MatchMode __MatchMode_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "IGNORE": return MatchMode.IGNORE;
      case "CONTAINS": return MatchMode.CONTAINS;
      case "EXACT": return MatchMode.EXACT;
      case "REGEX": return MatchMode.REGEX;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
