package com.serein.stats.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────────────

@Entity(tableName = "usage_sessions")
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val date: String,          // "yyyy-MM-dd"
    val durationMinutes: Int,
    val recordedAt: Long = System.currentTimeMillis()
)

/** A durable daily snapshot. One row per app per calendar day, updated as the day progresses. */
@Entity(tableName = "daily_usage", primaryKeys = ["date", "packageName"])
data class DailyUsage(
    val date: String,          // yyyy-MM-dd, local device day
    val packageName: String,
    val appLabel: String,
    val durationMinutes: Long,
    val recordedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int   // 0 = no limit
)

// ── DAOs ───────────────────────────────────────────────────────

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE date = :date ORDER BY durationMinutes DESC")
    fun getByDate(date: String): Flow<List<UsageSession>>

    @Query("SELECT * FROM usage_sessions WHERE date >= :from ORDER BY date DESC")
    fun getFrom(from: String): Flow<List<UsageSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UsageSession)

    @Query("DELETE FROM usage_sessions WHERE date < :before")
    suspend fun deleteOlderThan(before: String)
}

@Dao
interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<DailyUsage>)

    @Query("SELECT * FROM daily_usage WHERE date >= :from ORDER BY date ASC")
    fun getFrom(from: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE date >= :from ORDER BY date ASC")
    suspend fun getFromOnce(from: String): List<DailyUsage>
}

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY appLabel ASC")
    fun getAll(): Flow<List<AppLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(limit: AppLimit)

    @Delete
    suspend fun delete(limit: AppLimit)
}

// ── Database ───────────────────────────────────────────────────

@Database(
    entities = [UsageSession::class, DailyUsage::class, AppLimit::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appLimitDao(): AppLimitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "serein.db")
                    .addMigrations(object : Migration(1, 2) {
                        override fun migrate(db: SupportSQLiteDatabase) {
                            db.execSQL("""
                                CREATE TABLE IF NOT EXISTS daily_usage (
                                    date TEXT NOT NULL,
                                    packageName TEXT NOT NULL,
                                    appLabel TEXT NOT NULL,
                                    durationMinutes INTEGER NOT NULL,
                                    recordedAt INTEGER NOT NULL,
                                    PRIMARY KEY(date, packageName)
                                )
                            """.trimIndent())
                        }
                    })
                    .build().also { INSTANCE = it }
            }
    }
}
