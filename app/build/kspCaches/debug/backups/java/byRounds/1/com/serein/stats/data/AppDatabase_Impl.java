package com.serein.stats.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile UsageSessionDao _usageSessionDao;

  private volatile AppLimitDao _appLimitDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `usage_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `date` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_limits` (`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `dailyLimitMinutes` INTEGER NOT NULL, PRIMARY KEY(`packageName`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '38653ded5b6f831101d44dad89e73957')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `usage_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `app_limits`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsUsageSessions = new HashMap<String, TableInfo.Column>(6);
        _columnsUsageSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsageSessions.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsageSessions.put("appLabel", new TableInfo.Column("appLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsageSessions.put("date", new TableInfo.Column("date", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsageSessions.put("durationMinutes", new TableInfo.Column("durationMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUsageSessions.put("recordedAt", new TableInfo.Column("recordedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUsageSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUsageSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUsageSessions = new TableInfo("usage_sessions", _columnsUsageSessions, _foreignKeysUsageSessions, _indicesUsageSessions);
        final TableInfo _existingUsageSessions = TableInfo.read(db, "usage_sessions");
        if (!_infoUsageSessions.equals(_existingUsageSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "usage_sessions(com.serein.stats.data.UsageSession).\n"
                  + " Expected:\n" + _infoUsageSessions + "\n"
                  + " Found:\n" + _existingUsageSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsAppLimits = new HashMap<String, TableInfo.Column>(3);
        _columnsAppLimits.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAppLimits.put("appLabel", new TableInfo.Column("appLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAppLimits.put("dailyLimitMinutes", new TableInfo.Column("dailyLimitMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAppLimits = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAppLimits = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAppLimits = new TableInfo("app_limits", _columnsAppLimits, _foreignKeysAppLimits, _indicesAppLimits);
        final TableInfo _existingAppLimits = TableInfo.read(db, "app_limits");
        if (!_infoAppLimits.equals(_existingAppLimits)) {
          return new RoomOpenHelper.ValidationResult(false, "app_limits(com.serein.stats.data.AppLimit).\n"
                  + " Expected:\n" + _infoAppLimits + "\n"
                  + " Found:\n" + _existingAppLimits);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "38653ded5b6f831101d44dad89e73957", "adcf54dcef2ca7decf1aecc029ae88b9");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "usage_sessions","app_limits");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `usage_sessions`");
      _db.execSQL("DELETE FROM `app_limits`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(UsageSessionDao.class, UsageSessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AppLimitDao.class, AppLimitDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public UsageSessionDao usageSessionDao() {
    if (_usageSessionDao != null) {
      return _usageSessionDao;
    } else {
      synchronized(this) {
        if(_usageSessionDao == null) {
          _usageSessionDao = new UsageSessionDao_Impl(this);
        }
        return _usageSessionDao;
      }
    }
  }

  @Override
  public AppLimitDao appLimitDao() {
    if (_appLimitDao != null) {
      return _appLimitDao;
    } else {
      synchronized(this) {
        if(_appLimitDao == null) {
          _appLimitDao = new AppLimitDao_Impl(this);
        }
        return _appLimitDao;
      }
    }
  }
}
