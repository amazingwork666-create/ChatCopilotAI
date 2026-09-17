package com.chatcopilot.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "knowledge_entries")
data class KnowledgeEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_entries ORDER BY category ASC, title ASC")
    suspend fun getAll(): List<KnowledgeEntry>

    @Query("SELECT * FROM knowledge_entries ORDER BY category ASC, title ASC")
    fun getAllLive(): androidx.lifecycle.LiveData<List<KnowledgeEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntry): Long

    @Update
    suspend fun update(entry: KnowledgeEntry)

    @Delete
    suspend fun delete(entry: KnowledgeEntry)

    @Query("DELETE FROM knowledge_entries")
    suspend fun deleteAll()
}

@Database(
    entities = [KnowledgeEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatcopilot.db"
                ).build().also { INSTANCE = it }
            }
    }
}
