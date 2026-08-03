package com.bam.sshfs.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.data.model.Identity
import com.bam.sshfs.data.model.SshKey

/**
 * The app's single Room database: keys, identities, and hosts.
 *
 * Foreign keys are enforced at the SQLite level, so a delete that would orphan a
 * reference throws instead of silently succeeding — the repositories unlink first.
 */
@Database(
    entities = [SshKey::class, Identity::class, Host::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SshfsDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
    abstract fun identityDao(): IdentityDao
    abstract fun hostDao(): HostDao

    companion object {
        private const val NAME = "sshfs.db"

        @Volatile private var instance: SshfsDatabase? = null

        /** The process-wide database, opened on first use. */
        fun get(context: Context): SshfsDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SshfsDatabase =
            Room.databaseBuilder(context, SshfsDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
