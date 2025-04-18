// UserDao.kt
package com.example.explorelens.data.db

import android.content.Context
import androidx.room.*
import com.example.explorelens.data.db.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: User)

    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getUser(): User?

    @Query("SELECT * FROM user LIMIT 1")
    fun observeUser(): Flow<User?>

    @Query("DELETE FROM user")
    suspend fun deleteUser()
}

@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class AuthDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AuthDatabase? = null

        fun getDatabase(context: Context): AuthDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AuthDatabase::class.java,
                    "auth_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}