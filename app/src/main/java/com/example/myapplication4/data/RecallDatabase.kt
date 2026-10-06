package com.example.myapplication4.data

import android.content.Context
import androidx.room.ColumnInfo
import com.example.myapplication4.domain.PronunciationTarget
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.myapplication4.domain.DailyReviewCount
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private fun id() = UUID.randomUUID().toString()

@Entity(indices = [Index("subjectId")], foreignKeys = [ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE)])
data class SubjectResourceEntity(@PrimaryKey val id: String = id(), val subjectId: String, val title: String, val kind: String = "note", val note: String = "", val uri: String? = null, val mimeType: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(indices = [Index(value = ["name"], unique = true)])
data class SubjectEntity(@PrimaryKey val id: String = id(), val name: String, val icon: String = "book", val accent: String = "blue", val createdAt: Long = System.currentTimeMillis(), val position: Int = 0, val archived: Boolean = false)

@Entity(indices = [Index("subjectId")], foreignKeys = [ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE)])
data class ChapterEntity(@PrimaryKey val id: String = id(), val subjectId: String, val name: String, val position: Int = 0, val createdAt: Long = System.currentTimeMillis())

@Entity(indices = [Index("subjectId"), Index("chapterId")], foreignKeys = [ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.SET_NULL)])
data class LessonEntity(@PrimaryKey val id: String = id(), val subjectId: String, val chapterId: String? = null, val title: String, val summary: String? = null, val notes: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), val archived: Boolean = false, @ColumnInfo(defaultValue = "'general'") val contentType: String = "general", val learningLanguage: String? = null)

@Entity(indices = [Index("lessonId")], foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.CASCADE)])
data class CardEntity(@PrimaryKey val id: String = id(), val lessonId: String, val type: String = "qa", val front: String, val back: String, val hint: String? = null, val sourceReference: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(), val suspended: Boolean = false)


@Entity(tableName = "card_pronunciation_targets", indices = [Index("cardId")], foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)])
data class PronunciationTargetEntity(@PrimaryKey val id: String = id(), val cardId: String, val side: String, val text: String, val language: String?, val occurrenceIndex: Int = 1) {
    fun target() = PronunciationTarget(side, text, language, occurrenceIndex)
}
fun PronunciationTarget.entity(cardId: String) = PronunciationTargetEntity(cardId = cardId, side = side, text = text, language = language, occurrenceIndex = occurrence)

@Entity(indices = [Index(value = ["name"], unique = true)]) data class TagEntity(@PrimaryKey val id: String = id(), val name: String)
@Entity(primaryKeys = ["lessonId", "tagId"], indices = [Index("tagId")]) data class LessonTagEntity(val lessonId: String, val tagId: String)
@Entity(indices = [Index("dueAt")], foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)])
data class ReviewStateEntity(@PrimaryKey val cardId: String, val state: String = "new", val dueAt: Long = System.currentTimeMillis(), val lastReviewedAt: Long? = null, val stability: Double = 0.4, val difficulty: Double = 5.0, val scheduledDays: Int = 0, val reps: Int = 0, val lapses: Int = 0)
@Entity(indices = [Index("cardId"), Index("reviewedAt")])
data class ReviewLogEntity(
    @PrimaryKey val id: String = id(),
    val cardId: String,
    val reviewedAt: Long,
    val rating: Int,
    val previousInterval: Int,
    val nextInterval: Int,
    val previousStability: Double,
    val newStability: Double,
    val durationMillis: Long,
    @ColumnInfo(defaultValue = "0") val previousDueAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val nextDueAt: Long = 0,
    @ColumnInfo(defaultValue = "0.0") val elapsedDays: Double = 0.0,
    @ColumnInfo(defaultValue = "5.0") val previousDifficulty: Double = 5.0,
    @ColumnInfo(defaultValue = "5.0") val newDifficulty: Double = 5.0,
    @ColumnInfo(defaultValue = "'new'") val previousState: String = "new",
    @ColumnInfo(defaultValue = "'new'") val newState: String = "new",
    @ColumnInfo(defaultValue = "0") val reps: Int = 0,
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
)
@Entity(indices = [Index("enabled")]) data class ScheduleBlockEntity(@PrimaryKey val id: String = id(), val name: String, val type: String, val startMinute: Int, val endMinute: Int, val days: String, val enabled: Boolean = true, val preferredFilter: String? = null)
@Entity(indices = [Index("importedAt")]) data class ImportRecordEntity(@PrimaryKey val id: String = id(), val fingerprint: String, val importedAt: Long = System.currentTimeMillis(), val source: String = "paste")

data class LessonOverview(val id: String, val title: String, val summary: String?, val subjectName: String, val subjectId: String, val chapterName: String?, val accent: String, val total: Int, val due: Int, val learned: Int, val difficult: Int, val nextDue: Long?, val lastReviewed: Long?, val chapterId: String? = null, val learningLanguage: String? = null)
data class CardWithLesson(val id: String, val lessonId: String, val type: String, val front: String, val back: String, val hint: String?, val sourceReference: String?, val suspended: Boolean, val lessonTitle: String, val subjectName: String, val state: String, val dueAt: Long, val lastReviewedAt: Long?, val stability: Double, val difficulty: Double, val scheduledDays: Int, val reps: Int, val lapses: Int, val learningLanguage: String? = null)
data class BackupData(val subjects: List<SubjectEntity>, val chapters: List<ChapterEntity>, val lessons: List<LessonEntity>, val cards: List<CardEntity>, val tags: List<TagEntity>, val lessonTags: List<LessonTagEntity>, val states: List<ReviewStateEntity>, val logs: List<ReviewLogEntity>, val schedules: List<ScheduleBlockEntity>, val imports: List<ImportRecordEntity>, val resources: List<SubjectResourceEntity> = emptyList(), val pronunciationTargets: List<PronunciationTargetEntity> = emptyList())

@Dao
interface RecallDao {
    @Query("SELECT COUNT(*) FROM CardEntity c JOIN ReviewStateEntity r ON r.cardId=c.id JOIN LessonEntity l ON l.id=c.lessonId JOIN SubjectEntity s ON s.id=l.subjectId WHERE c.suspended=0 AND l.archived=0 AND s.archived=0 AND r.dueAt<=:now")
    suspend fun reminderDueCount(now: Long): Int
    @Query("SELECT * FROM card_pronunciation_targets WHERE cardId=:cardId ORDER BY side, occurrenceIndex") fun pronunciationTargets(cardId: String): Flow<List<PronunciationTargetEntity>>
    @Query("SELECT * FROM card_pronunciation_targets WHERE cardId=:cardId") suspend fun targetsForCard(cardId: String): List<PronunciationTargetEntity>
    @Query("SELECT * FROM card_pronunciation_targets") suspend fun allPronunciationTargets(): List<PronunciationTargetEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPronunciationTargets(targets: List<PronunciationTargetEntity>)
    @Query("DELETE FROM card_pronunciation_targets WHERE cardId=:cardId") suspend fun deletePronunciationTargets(cardId: String)
    @Transaction suspend fun replacePronunciationTargets(cardId: String, targets: List<PronunciationTargetEntity>) { deletePronunciationTargets(cardId); insertPronunciationTargets(targets) }

    @Query("SELECT * FROM SubjectResourceEntity WHERE subjectId=:subjectId ORDER BY updatedAt DESC") fun resources(subjectId: String): Flow<List<SubjectResourceEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertResource(value: SubjectResourceEntity)
    @Update suspend fun updateResource(value: SubjectResourceEntity)
    @Query("DELETE FROM SubjectResourceEntity WHERE id=:id") suspend fun deleteResource(id: String)
    @Update suspend fun updateSubject(value: SubjectEntity)
    @Update suspend fun updateChapter(value: ChapterEntity)
    @Update suspend fun updateLesson(value: LessonEntity)
    @Update suspend fun updateCard(value: CardEntity)
    @Update suspend fun updateTag(value: TagEntity)
    @Query("SELECT * FROM LessonEntity WHERE id=:id") suspend fun lessonById(id: String): LessonEntity?
    @Query("SELECT * FROM LessonEntity WHERE subjectId=:subjectId AND chapterId IS :chapterId AND lower(trim(title))=lower(trim(:title)) LIMIT 1") suspend fun findLessonInChapter(subjectId: String, chapterId: String?, title: String): LessonEntity?
    @Query("SELECT front FROM CardEntity WHERE lessonId=:lessonId") suspend fun cardFronts(lessonId: String): List<String>
    @Query("SELECT * FROM SubjectEntity WHERE id=:id") suspend fun subjectById(id: String): SubjectEntity?
    @Query("SELECT * FROM SubjectEntity WHERE archived = 0 ORDER BY position, name") fun subjects(): Flow<List<SubjectEntity>>
    @Query("SELECT * FROM SubjectEntity WHERE id = :id") fun subject(id: String): Flow<SubjectEntity?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSubject(subject: SubjectEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChapter(chapter: ChapterEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLesson(lesson: LessonEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCard(card: CardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveState(state: ReviewStateEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addLog(log: ReviewLogEntity)
    @Transaction suspend fun commitReview(state: ReviewStateEntity, log: ReviewLogEntity) { saveState(state); addLog(log) }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(tag: TagEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun tagLesson(link: LessonTagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSchedule(block: ScheduleBlockEntity)
    @Query("DELETE FROM ScheduleBlockEntity WHERE id = :id") suspend fun deleteSchedule(id: String)
    @Query("DELETE FROM ReviewLogEntity WHERE cardId IN (SELECT c.id FROM CardEntity c JOIN LessonEntity l ON l.id=c.lessonId WHERE l.subjectId=:id)") suspend fun deleteSubjectLogs(id: String)
    @Query("DELETE FROM LessonTagEntity WHERE lessonId IN (SELECT id FROM LessonEntity WHERE subjectId=:id)") suspend fun deleteSubjectTagLinks(id: String)
    @Query("DELETE FROM SubjectEntity WHERE id = :id") suspend fun deleteSubjectRow(id: String)
    @Query("DELETE FROM ChapterEntity WHERE id = :id") suspend fun deleteChapter(id: String)
    @Query("DELETE FROM ReviewLogEntity WHERE cardId IN (SELECT id FROM CardEntity WHERE lessonId=:id)") suspend fun deleteLessonLogs(id: String)
    @Query("DELETE FROM LessonTagEntity WHERE lessonId=:id") suspend fun deleteLessonTagLinks(id: String)
    @Query("DELETE FROM LessonEntity WHERE id = :id") suspend fun deleteLessonRow(id: String)
    @Query("DELETE FROM TagEntity WHERE id NOT IN (SELECT tagId FROM LessonTagEntity)") suspend fun deleteUnusedTags()
    @Query("DELETE FROM LessonTagEntity WHERE lessonId=:lessonId AND tagId=:tagId") suspend fun removeLessonTagLink(lessonId: String, tagId: String)
    @Query("SELECT * FROM ScheduleBlockEntity ORDER BY type, startMinute") fun schedules(): Flow<List<ScheduleBlockEntity>>
    @Query("SELECT * FROM ChapterEntity WHERE subjectId = :subjectId ORDER BY position, name") fun chapters(subjectId: String): Flow<List<ChapterEntity>>
    @Query("SELECT l.id, l.learningLanguage, l.chapterId, l.title, l.summary, s.name subjectName, s.id subjectId, ch.name chapterName, s.accent, COUNT(c.id) total, SUM(CASE WHEN rs.dueAt <= :now AND c.suspended = 0 THEN 1 ELSE 0 END) due, SUM(CASE WHEN rs.reps > 0 THEN 1 ELSE 0 END) learned, SUM(CASE WHEN rs.difficulty >= 7 THEN 1 ELSE 0 END) difficult, MIN(CASE WHEN c.suspended = 0 THEN rs.dueAt END) nextDue, MAX(rs.lastReviewedAt) lastReviewed FROM LessonEntity l JOIN SubjectEntity s ON s.id = l.subjectId LEFT JOIN ChapterEntity ch ON ch.id = l.chapterId LEFT JOIN CardEntity c ON c.lessonId = l.id LEFT JOIN ReviewStateEntity rs ON rs.cardId = c.id WHERE l.archived = 0 GROUP BY l.id ORDER BY due DESC, l.updatedAt DESC") fun lessonOverviews(now: Long): Flow<List<LessonOverview>>
    @Query("SELECT l.id, l.learningLanguage, l.chapterId, l.title, l.summary, s.name subjectName, s.id subjectId, ch.name chapterName, s.accent, COUNT(c.id) total, SUM(CASE WHEN rs.dueAt <= :now AND c.suspended = 0 THEN 1 ELSE 0 END) due, SUM(CASE WHEN rs.reps > 0 THEN 1 ELSE 0 END) learned, SUM(CASE WHEN rs.difficulty >= 7 THEN 1 ELSE 0 END) difficult, MIN(CASE WHEN c.suspended = 0 THEN rs.dueAt END) nextDue, MAX(rs.lastReviewedAt) lastReviewed FROM LessonEntity l JOIN SubjectEntity s ON s.id = l.subjectId LEFT JOIN ChapterEntity ch ON ch.id = l.chapterId LEFT JOIN CardEntity c ON c.lessonId = l.id LEFT JOIN ReviewStateEntity rs ON rs.cardId = c.id WHERE l.subjectId = :subjectId AND l.archived = 0 GROUP BY l.id ORDER BY ch.position, l.title") fun lessonOverviewsForSubject(subjectId: String, now: Long): Flow<List<LessonOverview>>
    @Query("SELECT c.id, c.lessonId, c.type, c.front, c.back, c.hint, c.sourceReference, c.suspended, l.title lessonTitle, l.learningLanguage, s.name subjectName, rs.state, rs.dueAt, rs.lastReviewedAt, rs.stability, rs.difficulty, rs.scheduledDays, rs.reps, rs.lapses FROM CardEntity c JOIN LessonEntity l ON l.id = c.lessonId JOIN SubjectEntity s ON s.id = l.subjectId JOIN ReviewStateEntity rs ON rs.cardId = c.id WHERE c.suspended = 0 AND rs.dueAt <= :now ORDER BY rs.dueAt, RANDOM()") suspend fun dueCards(now: Long): List<CardWithLesson>
    @Query("SELECT c.id, c.lessonId, c.type, c.front, c.back, c.hint, c.sourceReference, c.suspended, l.title lessonTitle, l.learningLanguage, s.name subjectName, rs.state, rs.dueAt, rs.lastReviewedAt, rs.stability, rs.difficulty, rs.scheduledDays, rs.reps, rs.lapses FROM CardEntity c JOIN LessonEntity l ON l.id = c.lessonId JOIN SubjectEntity s ON s.id = l.subjectId JOIN ReviewStateEntity rs ON rs.cardId = c.id WHERE c.lessonId = :lessonId AND c.suspended = 0 ORDER BY RANDOM()") suspend fun lessonCards(lessonId: String): List<CardWithLesson>
    @Query("SELECT * FROM CardEntity WHERE lessonId = :lessonId ORDER BY createdAt DESC") fun cardsForLesson(lessonId: String): Flow<List<CardEntity>>
    @Query("SELECT t.* FROM TagEntity t JOIN LessonTagEntity lt ON lt.tagId = t.id WHERE lt.lessonId = :lessonId ORDER BY t.name") fun tagsForLesson(lessonId: String): Flow<List<TagEntity>>
    @Query("SELECT COUNT(*) FROM CardEntity c JOIN ReviewStateEntity rs ON rs.cardId=c.id WHERE c.suspended=0 AND rs.dueAt <= :now") fun dueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM ReviewLogEntity WHERE reviewedAt >= :since") fun reviewCountSince(since: Long): Flow<Int>
    @RawQuery(observedEntities = [ReviewLogEntity::class])
    fun studyActivity(query: SupportSQLiteQuery): Flow<List<DailyReviewCount>>
    @Query("UPDATE CardEntity SET suspended = :suspended, updatedAt = :now WHERE id = :cardId") suspend fun setSuspended(cardId: String, suspended: Boolean, now: Long)
    @Query("DELETE FROM ReviewLogEntity WHERE cardId=:cardId") suspend fun deleteCardLogs(cardId: String)
    @Query("DELETE FROM CardEntity WHERE id = :cardId") suspend fun deleteCardRow(cardId: String)
    @Query("SELECT EXISTS(SELECT 1 FROM CardEntity WHERE lessonId=:lessonId AND lower(front)=lower(:front))") suspend fun duplicateCard(lessonId: String, front: String): Boolean
    @Query("SELECT * FROM LessonEntity WHERE subjectId=:subjectId AND lower(title)=lower(:title) LIMIT 1") suspend fun findLesson(subjectId: String, title: String): LessonEntity?
    @Query("SELECT * FROM SubjectEntity WHERE lower(name)=lower(:name) LIMIT 1") suspend fun findSubject(name: String): SubjectEntity?
    @Query("SELECT * FROM ChapterEntity WHERE subjectId=:subjectId AND lower(name)=lower(:name) LIMIT 1") suspend fun findChapter(subjectId: String, name: String): ChapterEntity?
    @Query("SELECT * FROM TagEntity WHERE lower(name)=lower(:name) LIMIT 1") suspend fun findTag(name: String): TagEntity?

    @Query("SELECT * FROM SubjectResourceEntity") suspend fun allResources(): List<SubjectResourceEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeResources(items: List<SubjectResourceEntity>)
    @Query("SELECT * FROM SubjectEntity") suspend fun allSubjects(): List<SubjectEntity>
    @Query("SELECT * FROM ChapterEntity") suspend fun allChapters(): List<ChapterEntity>
    @Query("SELECT * FROM LessonEntity") suspend fun allLessons(): List<LessonEntity>
    @Query("SELECT * FROM CardEntity") suspend fun allCards(): List<CardEntity>
    @Query("SELECT * FROM TagEntity") suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM LessonTagEntity") suspend fun allLessonTags(): List<LessonTagEntity>
    @Query("SELECT * FROM ReviewStateEntity") suspend fun allStates(): List<ReviewStateEntity>
    @Query("SELECT * FROM ReviewLogEntity") suspend fun allLogs(): List<ReviewLogEntity>
    @Query("SELECT * FROM ScheduleBlockEntity") suspend fun allSchedules(): List<ScheduleBlockEntity>
    @Query("SELECT * FROM ImportRecordEntity") suspend fun allImports(): List<ImportRecordEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeSubjects(items: List<SubjectEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeChapters(items: List<ChapterEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeLessons(items: List<LessonEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeCards(items: List<CardEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeTags(items: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeLessonTags(items: List<LessonTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeStates(items: List<ReviewStateEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeLogs(items: List<ReviewLogEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeSchedules(items: List<ScheduleBlockEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mergeImports(items: List<ImportRecordEntity>)

    @Transaction suspend fun deleteCard(cardId: String) { deleteCardLogs(cardId); deleteCardRow(cardId) }
    @Transaction suspend fun deleteLesson(id: String) { deleteLessonLogs(id); deleteLessonTagLinks(id); deleteLessonRow(id); deleteUnusedTags() }
    @Transaction suspend fun deleteSubject(id: String) { deleteSubjectLogs(id); deleteSubjectTagLinks(id); deleteSubjectRow(id); deleteUnusedTags() }
    @Transaction suspend fun removeLessonTag(lessonId: String, tagId: String) { removeLessonTagLink(lessonId, tagId); deleteUnusedTags() }
    @Transaction suspend fun backup() = BackupData(allSubjects(), allChapters(), allLessons(), allCards(), allTags(), allLessonTags(), allStates(), allLogs(), allSchedules(), allImports(), allResources(), allPronunciationTargets())
    @Transaction suspend fun mergeBackup(data: BackupData) { val existingCards = allCards().map { it.id }.toSet(); mergeSubjects(data.subjects); mergeChapters(data.chapters); mergeLessons(data.lessons); mergeCards(data.cards); mergeTags(data.tags); mergeLessonTags(data.lessonTags); mergeStates(data.states); mergeLogs(data.logs); mergeSchedules(data.schedules); mergeImports(data.imports); mergeResources(data.resources); insertPronunciationTargets(data.pronunciationTargets.filter { it.cardId !in existingCards }) }
}

@Database(entities = [SubjectEntity::class, ChapterEntity::class, LessonEntity::class, CardEntity::class, TagEntity::class, LessonTagEntity::class, ReviewStateEntity::class, ReviewLogEntity::class, ScheduleBlockEntity::class, ImportRecordEntity::class, SubjectResourceEntity::class, PronunciationTargetEntity::class], version = 4, exportSchema = true)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun dao(): RecallDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS SubjectResourceEntity (id TEXT NOT NULL PRIMARY KEY, subjectId TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL, uri TEXT, mimeType TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(subjectId) REFERENCES SubjectEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_SubjectResourceEntity_subjectId ON SubjectResourceEntity(subjectId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE LessonEntity ADD COLUMN contentType TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE LessonEntity ADD COLUMN learningLanguage TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS card_pronunciation_targets (id TEXT NOT NULL PRIMARY KEY, cardId TEXT NOT NULL, side TEXT NOT NULL, text TEXT NOT NULL, language TEXT, occurrenceIndex INTEGER NOT NULL, FOREIGN KEY(cardId) REFERENCES CardEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_card_pronunciation_targets_cardId ON card_pronunciation_targets(cardId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN previousDueAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN nextDueAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN elapsedDays REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN previousDifficulty REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN newDifficulty REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN previousState TEXT NOT NULL DEFAULT 'new'")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN newState TEXT NOT NULL DEFAULT 'new'")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ReviewLogEntity ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, RecallDatabase::class.java, "recall.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
