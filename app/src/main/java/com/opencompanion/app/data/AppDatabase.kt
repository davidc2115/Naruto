package com.opencompanion.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CharacterEntity::class, ChatMessageEntity::class, UserPersonaEntity::class, VectorMemoryEntity::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
    abstract fun userPersonaDao(): UserPersonaDao
    abstract fun vectorMemoryDao(): VectorMemoryDao

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

        /** Ajoute la mémoire éditable et le niveau de relation par personnage (voir
         *  [CharacterEntity.memoryNotes]/[CharacterEntity.affectionLevel]). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `memoryNotes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `affectionLevel` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Ajoute la galerie médias (photos, GIFs, vidéos) par personnage. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `galleryMediaJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Ajoute la base de données vectorielle locale et les tags de mémoire conversationnelle (v5). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vector_memories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`characterId` INTEGER NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`role` TEXT, " +
                        "`tags` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`vectorBlob` BLOB NOT NULL, " +
                        "`importance` REAL NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vector_memories_characterId` ON `vector_memories` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vector_memories_category` ON `vector_memories` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vector_memories_timestamp` ON `vector_memories` (`timestamp`)")
            }
        }

        /** Ajoute deletedMediaJson et isCustomizedByUser pour sanctuariser les modifications utilisateur (v6). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `deletedMediaJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `characters` ADD COLUMN `isCustomizedByUser` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencompanion.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
