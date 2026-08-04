// ===== AppDatabase.kt =====
// Room 本地数据库
package com.dramatica.flow.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---- 创作步骤枚举 ----
enum class DramaticaStep(val number: Int, val label: String) {
    BASIC_INFO(1, "基础信息"),
    WORLD_BUILDING(2, "世界观构建"),
    CHARACTER_DESIGN(3, "角色设计"),
    OUTLINE(4, "大纲规划"),
    WRITING(5, "章节创作"),
    AI_RESULT(6, "AI创作结果"),
    TIMELINE(7, "时间线");

    val next: DramaticaStep?
        get() = when (this) {
            BASIC_INFO -> WORLD_BUILDING
            WORLD_BUILDING -> CHARACTER_DESIGN
            CHARACTER_DESIGN -> OUTLINE
            OUTLINE -> WRITING
            WRITING -> AI_RESULT
            AI_RESULT -> TIMELINE
            TIMELINE -> null
        }

    val prev: DramaticaStep?
        get() = when (this) {
            BASIC_INFO -> null
            WORLD_BUILDING -> BASIC_INFO
            CHARACTER_DESIGN -> WORLD_BUILDING
            OUTLINE -> CHARACTER_DESIGN
            WRITING -> OUTLINE
            AI_RESULT -> WRITING
            TIMELINE -> AI_RESULT
        }
}

// ---- 故事配置 ----
data class StoryConfig(
    val title: String = "",
    val genre: String = "玄幻",
    val briefIdea: String = "",
    val targetChapters: Int = 30,
    val coreSetting: String = "",
    val characters: String = "",
    val outline: String = "",
    val colloquialStyle: Boolean = false,  // 口语化创作
    val useMemes: Boolean = false,          // 引用梗写作
    val referenceAnalysis: String = ""      // 功能A：参考小说分析结果
)

// ---- 创作项目实体 ----
@Entity(tableName = "dramatica_projects")
data class DramaticaProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String = "",
    val title: String = "",
    val genre: String = "玄幻",
    val briefIdea: String = "",
    val targetChapters: Int = 30,
    val coreSetting: String = "",
    val characters: String = "",
    val outline: String = "",
    val causalChainHistory: String = "",
    val summaryHistory: String = "",
    val pendingHooks: String = "",
    val emotionalArcs: String = "",
    val colloquialStyle: Boolean = false,
    val useMemes: Boolean = false,
    val referenceAnalysis: String = "",
    val referenceCreationConfigured: Boolean = false,  // 参考创作是否已确认设定
    val currentStep: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ---- UI状态 ----
sealed class DramaticaUiState {
    object Idle : DramaticaUiState()
    data class AutoGenerating(val step: String, val message: String, val progress: Float = 0f) : DramaticaUiState()
    data class WorldGenerated(val worldSetting: String) : DramaticaUiState()
    data class CharactersGenerated(val characters: String) : DramaticaUiState()
    data class OutlineGenerated(val outline: String) : DramaticaUiState()
    data class WritingChapter(val step: String, val message: String, val progress: Float = 0f) : DramaticaUiState()
    data class Error(val message: String) : DramaticaUiState()
}

// ---- 实体定义 ----

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val genre: String = "玄幻",
    val targetChapters: Int = 90,
    val targetWords: Int = 4000,
    val currentChapter: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val chapterNumber: Int,
    val title: String = "",
    val content: String = "",
    val wordCount: Int = 0,
    val kind: String = "draft" // draft / final
)

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val name: String,
    val role: String = "",       // 主角/对立者/影响者/守护者/伙伴
    val avatar: String = "",
    val type: String = "protagonist",
    val description: String = "",
    val tags: String = ""        // 逗号分隔
)

@Entity(tableName = "hooks")
data class HookEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: String = "foreshadow",  // foreshadow/promise/mystery/conflict
    val description: String,
    val plantedChapter: Int,
    val resolvedChapter: Int? = null,
    val status: String = "open"       // open/resolved/warning
)

@Entity(tableName = "causal_links")
data class CausalLinkEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val chapter: Int,
    val cause: String,
    val event: String,
    val consequence: String,
    val decision: String = ""
)

@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val characterA: String,
    val characterB: String,
    val type: String = "neutral",
    val strength: Int = 0,        // -100 ~ 100
    val reason: String = ""
)

@Entity(tableName = "emotions")
data class EmotionEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val characterId: String,
    val emotion: String,
    val intensity: Int,           // 1-10
    val chapter: Int,
    val trigger: String = ""
)

@Entity(tableName = "timeline_events")
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val chapter: Int,
    val action: String,
    val type: String = "other",   // conflict/reveal/emotion/foreshadow/other
    val characterId: String = "",
    val location: String = ""
)

// ---- 信息边界 ----
@Entity(tableName = "known_info")
data class KnownInfoEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val characterId: String,       // 哪个角色知道的
    val infoKey: String,            // 信息关键词
    val content: String,            // 具体内容
    val learnedInChapter: Int,      // 在哪一章知道的
    val source: String = "witnessed" // witnessed/hearsay/deduced/document
)

// ---- 多线叙事 ----
@Entity(tableName = "narrative_threads")
data class NarrativeThreadEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,               // 线程名称
    val type: String = "main",      // main/sub/parallel/flashback
    val povCharacterId: String = "",// 视角角色
    val goalArc: String = "",       // 目标弧线
    val weight: Float = 1.0f,       // 篇幅权重
    val lastActiveChapter: Int = 0, // 最后活跃章节
    val status: String = "active"   // active/dropped/completed
)

// ---- 戏剧节拍 ----
@Entity(tableName = "chapter_beats")
data class ChapterBeatEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val bookId: String,
    val chapter: Int,
    val beatType: String,           // setup/inciting_incident/turning_point/midpoint/crisis/climax/revelation/decision/consequence/transition
    val description: String = "",
    val characterId: String = "",
    val emotionalTarget: String = ""// 情感目标
)

// ---- 写作技能（功能B：风格蒸馏）----
@Entity(tableName = "writing_skills")
data class WritingSkillEntity(
    @PrimaryKey val bookId: String,          // 一对一绑定书籍
    val sourceNovel: String = "",            // 参考小说文件名
    val styleProfile: String = "",           // 蒸馏后的完整风格描述（注入 prompt 用）
    val sentencePatterns: String = "",       // 句式模式
    val vocabularyFingerprint: String = "",  // 词汇指纹
    val narrativeStyle: String = "",         // 叙事手法
    val dialogueStyle: String = "",          // 对话风格
    val pacingStyle: String = "",            // 节奏特征
    val createdAt: Long = System.currentTimeMillis()
)

// ---- DAO ----

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("UPDATE books SET currentChapter = :chapter WHERE id = :bookId")
    suspend fun updateCurrentChapter(bookId: String, chapter: Int)

    @Query("UPDATE books SET title = :newTitle WHERE id = :bookId")
    suspend fun updateTitle(bookId: String, newTitle: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterNumber")
    fun getChapters(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterNumber = :num")
    suspend fun getChapter(bookId: String, num: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE bookId = :bookId AND chapterNumber = :num")
    suspend fun deleteChapter(bookId: String, num: Int)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE bookId = :bookId")
    fun getCharacters(bookId: String): Flow<List<CharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity)

    @Delete
    suspend fun deleteCharacter(character: CharacterEntity)
}

@Dao
interface HookDao {
    @Query("SELECT * FROM hooks WHERE bookId = :bookId ORDER BY plantedChapter")
    fun getHooks(bookId: String): Flow<List<HookEntity>>

    @Query("SELECT * FROM hooks WHERE bookId = :bookId AND status = :status")
    fun getHooksByStatus(bookId: String, status: String): Flow<List<HookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHook(hook: HookEntity)

    @Query("UPDATE hooks SET status = 'resolved', resolvedChapter = :chapter WHERE id = :hookId")
    suspend fun resolveHook(hookId: String, chapter: Int)
}

@Dao
interface CausalDao {
    @Query("SELECT * FROM causal_links WHERE bookId = :bookId ORDER BY chapter")
    fun getCausalChain(bookId: String): Flow<List<CausalLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: CausalLinkEntity)
}

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationships WHERE bookId = :bookId")
    fun getRelationships(bookId: String): Flow<List<RelationshipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: RelationshipEntity)

    @Query("UPDATE relationships SET strength = :strength, reason = :reason WHERE uid = :uid")
    suspend fun updateStrength(uid: Long, strength: Int, reason: String)
}

@Dao
interface EmotionDao {
    @Query("SELECT * FROM emotions WHERE bookId = :bookId ORDER BY chapter")
    fun getEmotions(bookId: String): Flow<List<EmotionEntity>>

    @Query("SELECT * FROM emotions WHERE bookId = :bookId AND characterId = :charId ORDER BY chapter")
    fun getCharacterEmotions(bookId: String, charId: String): Flow<List<EmotionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotion(emotion: EmotionEntity)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_events WHERE bookId = :bookId ORDER BY chapter")
    fun getTimeline(bookId: String): Flow<List<TimelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TimelineEntity)
}

@Dao
interface KnownInfoDao {
    @Query("SELECT * FROM known_info WHERE bookId = :bookId ORDER BY learnedInChapter")
    fun getKnownInfo(bookId: String): Flow<List<KnownInfoEntity>>

    @Query("SELECT * FROM known_info WHERE bookId = :bookId AND characterId = :charId")
    fun getCharacterKnownInfo(bookId: String, charId: String): Flow<List<KnownInfoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInfo(info: KnownInfoEntity)
}

@Dao
interface NarrativeThreadDao {
    @Query("SELECT * FROM narrative_threads WHERE bookId = :bookId ORDER BY type, name")
    fun getThreads(bookId: String): Flow<List<NarrativeThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: NarrativeThreadEntity)

    @Query("UPDATE narrative_threads SET lastActiveChapter = :chapter, status = :status WHERE id = :id")
    suspend fun updateThreadStatus(id: String, chapter: Int, status: String)

    @Delete
    suspend fun deleteThread(thread: NarrativeThreadEntity)
}

@Dao
interface ChapterBeatDao {
    @Query("SELECT * FROM chapter_beats WHERE bookId = :bookId ORDER BY chapter")
    fun getBeats(bookId: String): Flow<List<ChapterBeatEntity>>

    @Query("SELECT * FROM chapter_beats WHERE bookId = :bookId AND chapter = :chapter")
    fun getBeatsByChapter(bookId: String, chapter: Int): Flow<List<ChapterBeatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeat(beat: ChapterBeatEntity)

    @Delete
    suspend fun deleteBeat(beat: ChapterBeatEntity)
}

@Dao
interface WritingSkillDao {
    @Query("SELECT * FROM writing_skills WHERE bookId = :bookId")
    suspend fun getSkill(bookId: String): WritingSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: WritingSkillEntity)

    @Query("DELETE FROM writing_skills WHERE bookId = :bookId")
    suspend fun deleteSkill(bookId: String)
}

@Dao
interface DramaticaProjectDao {
    @Query("SELECT * FROM dramatica_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<DramaticaProjectEntity>>

    @Query("SELECT * FROM dramatica_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): DramaticaProjectEntity?

    @Query("SELECT * FROM dramatica_projects WHERE id = :id")
    fun getProjectByIdFlow(id: Long): Flow<DramaticaProjectEntity?>

    @Query("SELECT * FROM dramatica_projects WHERE bookId = :bookId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getProjectByBookId(bookId: String): DramaticaProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: DramaticaProjectEntity): Long

    @Update
    suspend fun updateProject(project: DramaticaProjectEntity)

    @Delete
    suspend fun deleteProject(project: DramaticaProjectEntity)
}

// ---- Database ----

@Database(
    entities = [
        BookEntity::class, ChapterEntity::class, CharacterEntity::class,
        HookEntity::class, CausalLinkEntity::class, RelationshipEntity::class,
        EmotionEntity::class, TimelineEntity::class, DramaticaProjectEntity::class,
        KnownInfoEntity::class, NarrativeThreadEntity::class, ChapterBeatEntity::class,
        WritingSkillEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun characterDao(): CharacterDao
    abstract fun hookDao(): HookDao
    abstract fun causalDao(): CausalDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun emotionDao(): EmotionDao
    abstract fun timelineDao(): TimelineDao
    abstract fun dramaticaProjectDao(): DramaticaProjectDao
    abstract fun knownInfoDao(): KnownInfoDao
    abstract fun narrativeThreadDao(): NarrativeThreadDao
    abstract fun chapterBeatDao(): ChapterBeatDao
    abstract fun writingSkillDao(): WritingSkillDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dramatica_projects ADD COLUMN bookId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dramatica_projects ADD COLUMN referenceCreationConfigured INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "dramatica_flow.db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
