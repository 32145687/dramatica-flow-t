// ===== LocalRepository.kt =====
// 本地数据仓库
package com.dramatica.flow.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class LocalRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val characterDao = db.characterDao()
    private val hookDao = db.hookDao()
    private val causalDao = db.causalDao()
    private val relationshipDao = db.relationshipDao()
    private val emotionDao = db.emotionDao()
    private val timelineDao = db.timelineDao()
    private val projectDao = db.dramaticaProjectDao()
    private val knownInfoDao = db.knownInfoDao()
    private val threadDao = db.narrativeThreadDao()
    private val beatDao = db.chapterBeatDao()
    private val skillDao = db.writingSkillDao()

    // ---- 书籍 ----
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()
    suspend fun getBook(id: String) = bookDao.getBook(id)
    suspend fun insertBook(book: BookEntity) = bookDao.insertBook(book)
    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)
    suspend fun updateCurrentChapter(bookId: String, chapter: Int) = bookDao.updateCurrentChapter(bookId, chapter)
    suspend fun updateBookTitle(bookId: String, newTitle: String) = bookDao.updateTitle(bookId, newTitle)

    // ---- 章节 ----
    fun getChapters(bookId: String): Flow<List<ChapterEntity>> = chapterDao.getChapters(bookId)
    suspend fun getChapter(bookId: String, num: Int) = chapterDao.getChapter(bookId, num)
    suspend fun saveChapter(chapter: ChapterEntity) = chapterDao.insertChapter(chapter)

    // ---- 角色 ----
    fun getCharacters(bookId: String): Flow<List<CharacterEntity>> = characterDao.getCharacters(bookId)
    suspend fun insertCharacter(character: CharacterEntity) = characterDao.insertCharacter(character)
    suspend fun deleteCharacter(character: CharacterEntity) = characterDao.deleteCharacter(character)

    // ---- 伏笔 ----
    fun getHooks(bookId: String): Flow<List<HookEntity>> = hookDao.getHooks(bookId)
    suspend fun insertHook(hook: HookEntity) = hookDao.insertHook(hook)
    suspend fun resolveHook(hookId: String, chapter: Int) = hookDao.resolveHook(hookId, chapter)

    // ---- 因果链 ----
    fun getCausalChain(bookId: String): Flow<List<CausalLinkEntity>> = causalDao.getCausalChain(bookId)
    suspend fun insertCausalLink(link: CausalLinkEntity) = causalDao.insertLink(link)

    // ---- 关系 ----
    fun getRelationships(bookId: String): Flow<List<RelationshipEntity>> = relationshipDao.getRelationships(bookId)
    suspend fun insertRelationship(rel: RelationshipEntity) = relationshipDao.insertRelationship(rel)
    suspend fun updateRelationshipStrength(uid: Long, strength: Int, reason: String) = relationshipDao.updateStrength(uid, strength, reason)

    // ---- 情感 ----
    fun getEmotions(bookId: String): Flow<List<EmotionEntity>> = emotionDao.getEmotions(bookId)
    suspend fun insertEmotion(emotion: EmotionEntity) = emotionDao.insertEmotion(emotion)

    // ---- 时间轴 ----
    fun getTimeline(bookId: String): Flow<List<TimelineEntity>> = timelineDao.getTimeline(bookId)
    suspend fun insertTimelineEvent(event: TimelineEntity) = timelineDao.insertEvent(event)

    // ---- 创作项目 ----
    fun getAllProjects(): Flow<List<DramaticaProjectEntity>> = projectDao.getAllProjects()
    suspend fun getProjectById(id: Long) = projectDao.getProjectById(id)
    fun getProjectByIdFlow(id: Long): Flow<DramaticaProjectEntity?> = projectDao.getProjectByIdFlow(id)
    suspend fun getProjectByBookId(bookId: String) = projectDao.getProjectByBookId(bookId)
    suspend fun insertProject(project: DramaticaProjectEntity) = projectDao.insertProject(project)
    suspend fun updateProject(project: DramaticaProjectEntity) = projectDao.updateProject(project)
    suspend fun deleteProject(project: DramaticaProjectEntity) = projectDao.deleteProject(project)

    // ---- 信息边界 ----
    fun getKnownInfo(bookId: String): Flow<List<KnownInfoEntity>> = knownInfoDao.getKnownInfo(bookId)
    fun getCharacterKnownInfo(bookId: String, charId: String): Flow<List<KnownInfoEntity>> = knownInfoDao.getCharacterKnownInfo(bookId, charId)
    suspend fun insertKnownInfo(info: KnownInfoEntity) = knownInfoDao.insertInfo(info)

    // ---- 多线叙事 ----
    fun getNarrativeThreads(bookId: String): Flow<List<NarrativeThreadEntity>> = threadDao.getThreads(bookId)
    suspend fun insertNarrativeThread(thread: NarrativeThreadEntity) = threadDao.insertThread(thread)
    suspend fun updateThreadStatus(id: String, chapter: Int, status: String) = threadDao.updateThreadStatus(id, chapter, status)
    suspend fun deleteNarrativeThread(thread: NarrativeThreadEntity) = threadDao.deleteThread(thread)

    // ---- 戏剧节拍 ----
    fun getChapterBeats(bookId: String): Flow<List<ChapterBeatEntity>> = beatDao.getBeats(bookId)
    fun getBeatsByChapter(bookId: String, chapter: Int): Flow<List<ChapterBeatEntity>> = beatDao.getBeatsByChapter(bookId, chapter)
    suspend fun insertChapterBeat(beat: ChapterBeatEntity) = beatDao.insertBeat(beat)
    suspend fun deleteChapterBeat(beat: ChapterBeatEntity) = beatDao.deleteBeat(beat)

    // ---- 写作技能 ----
    suspend fun getWritingSkill(bookId: String) = skillDao.getSkill(bookId)
    suspend fun saveWritingSkill(skill: WritingSkillEntity) = skillDao.insertSkill(skill)
    suspend fun deleteWritingSkill(bookId: String) = skillDao.deleteSkill(bookId)
}
