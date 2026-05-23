package com.serein.stats.data

import androidx.room.*
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
    entities = [UsageSession::class, AppLimit::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun appLimitDao(): AppLimitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "serein.db")
                    .build().also { INSTANCE = it }
            }
    }
}
