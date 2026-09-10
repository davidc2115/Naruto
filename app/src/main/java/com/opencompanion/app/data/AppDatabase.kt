package com.opencompanion.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CharacterEntity::class, ChatMessageEntity::class, UserPersonaEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun userPersonaDao(): UserPersonaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * Ajoute la table des personas utilisateur (voir [UserPersonaEntity]) et la colonne
         * [CharacterEntity.activePersonaId] qui y fait référence. Une vraie migration plutôt
         * qu'un `fallbackToDestructiveMigration()` : ce dernier effacerait silencieusement tous
         * les personnages et tout l'historique de conversation existants à la mise à jour de
         * l'app — inacceptable pour une app dont l'intérêt central est justement la mémoire
         * conversationnelle qui s'accumule dans le temps.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_personas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`age` INTEGER, " +
                        "`gender` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`avatarPath` TEXT, " +
                        "`isDefault` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `activePersonaId` INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencompanion.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
