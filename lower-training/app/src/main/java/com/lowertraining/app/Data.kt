package com.lowertraining.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max

const val SETS_PER_EXERCISE = 4

enum class RestClass { COMPOUND, ACCESSORY, SUPERSET }

data class ExerciseDef(
    val id: String,
    val name: String,
    val restClass: RestClass,
)

data class WorkoutPlan(
    val id: String,
    val name: String,
    val subtitle: String,
)

data class SetTarget(
    val exercise: ExerciseDef,
    val displaySet: Int,
    val totalSets: Int,
    val restAfter: Boolean,
    val supersetLabel: String? = null,
)

data class PreviousSet(
    val weight: Double,
    val reps: Int,
)

data class LoggedSet(
    val id: Long,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val displaySet: Int,
    val weight: Double,
    val reps: Int,
    val e1rm: Double,
    val timestamp: Long,
    val isPr: Boolean,
)

data class SessionSummary(
    val sessionId: Long,
    val planName: String,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolume: Double,
    val durationMillis: Long,
    val prCount: Int,
)

data class ExerciseAnalytics(
    val exerciseId: String,
    val exerciseName: String,
    val maxWeight: Double,
    val bestE1rm: Double,
    val totalVolume: Double,
    val totalSets: Int,
    val lastWeight: Double?,
    val lastReps: Int?,
    val recentTrendPercent: Double?,
)

data class WeeklyVolume(
    val label: String,
    val volume: Double,
)

data class RecentSession(
    val id: Long,
    val planName: String,
    val completedAt: Long,
    val totalVolume: Double,
    val totalSets: Int,
    val durationMillis: Long,
    val prCount: Int,
)

data class AnalyticsSnapshot(
    val sessionCount: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolume: Double,
    val totalPrs: Int,
    val bestE1rm: Double,
    val exercises: List<ExerciseAnalytics>,
    val weeklyVolume: List<WeeklyVolume>,
    val recentSessions: List<RecentSession>,
)

data class AppSettings(
    val compoundRestSec: Int = 180,
    val accessoryRestSec: Int = 90,
    val supersetRestSec: Int = 120,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

object WorkoutDefinitions {
    private val squat = ExerciseDef("barbell_squat", "Barbell Squat", RestClass.COMPOUND)
    private val rdl = ExerciseDef("romanian_deadlift", "Romanian Deadlift", RestClass.COMPOUND)
    private val legPress = ExerciseDef("leg_press", "Leg Press", RestClass.SUPERSET)
    private val calfPress = ExerciseDef("calf_press", "Calf Press", RestClass.SUPERSET)

    private val deadlift = ExerciseDef("deadlift", "Deadlift", RestClass.COMPOUND)
    private val legExtension = ExerciseDef("leg_extension", "Leg Extension Machine", RestClass.ACCESSORY)
    private val legCurl = ExerciseDef("leg_curl", "Leg Curl Machine", RestClass.ACCESSORY)
    private val calfMachine = ExerciseDef("calf_press_machine", "Calf Press Machine", RestClass.ACCESSORY)

    val lower1 = WorkoutPlan("lower_1", "Lower 1", "Squat · RDL · Leg Press / Calf Press")
    val lower2 = WorkoutPlan("lower_2", "Lower 2", "Deadlift · Extension · Curl · Calves")

    val plans = listOf(lower1, lower2)

    fun targets(plan: WorkoutPlan): List<SetTarget> = buildList {
        when (plan.id) {
            lower1.id -> {
                repeat(SETS_PER_EXERCISE) { i ->
                    add(SetTarget(squat, i + 1, SETS_PER_EXERCISE, restAfter = true))
                }
                repeat(SETS_PER_EXERCISE) { i ->
                    add(SetTarget(rdl, i + 1, SETS_PER_EXERCISE, restAfter = true))
                }
                repeat(SETS_PER_EXERCISE) { i ->
                    add(
                        SetTarget(
                            legPress,
                            i + 1,
                            SETS_PER_EXERCISE,
                            restAfter = false,
                            supersetLabel = "Superset ${i + 1}A",
                        )
                    )
                    add(
                        SetTarget(
                            calfPress,
                            i + 1,
                            SETS_PER_EXERCISE,
                            restAfter = true,
                            supersetLabel = "Superset ${i + 1}B",
                        )
                    )
                }
            }

            else -> {
                listOf(deadlift, legExtension, legCurl, calfMachine).forEach { exercise ->
                    repeat(SETS_PER_EXERCISE) { i ->
                        add(SetTarget(exercise, i + 1, SETS_PER_EXERCISE, restAfter = true))
                    }
                }
            }
        }
    }
}

class TrainingDb(context: Context) : SQLiteOpenHelper(context, "lower_training.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id TEXT NOT NULL,
                plan_name TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                total_volume REAL NOT NULL DEFAULT 0,
                total_sets INTEGER NOT NULL DEFAULT 0,
                total_reps INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                pr_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE sets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                exercise_id TEXT NOT NULL,
                exercise_name TEXT NOT NULL,
                display_set INTEGER NOT NULL,
                weight REAL NOT NULL,
                reps INTEGER NOT NULL,
                e1rm REAL NOT NULL,
                created_at INTEGER NOT NULL,
                is_pr INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_sets_exercise_created ON sets(exercise_id, created_at DESC)")
        db.execSQL("CREATE INDEX idx_sets_session ON sets(session_id)")
        db.execSQL("CREATE INDEX idx_sessions_completed ON sessions(completed_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN total_sets INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN total_reps INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN pr_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sets ADD COLUMN is_pr INTEGER NOT NULL DEFAULT 0")
        }
    }
}

class TrainingRepository(context: Context) {
    private val dbHelper = TrainingDb(context.applicationContext)
    private val prefs = context.getSharedPreferences("lower_training_settings", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings = AppSettings(
        compoundRestSec = prefs.getInt("compound_rest", 180),
        accessoryRestSec = prefs.getInt("accessory_rest", 90),
        supersetRestSec = prefs.getInt("superset_rest", 120),
        soundEnabled = prefs.getBoolean("sound_enabled", true),
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
    )

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putInt("compound_rest", settings.compoundRestSec)
            .putInt("accessory_rest", settings.accessoryRestSec)
            .putInt("superset_rest", settings.supersetRestSec)
            .putBoolean("sound_enabled", settings.soundEnabled)
            .putBoolean("haptics_enabled", settings.hapticsEnabled)
            .apply()
    }

    fun startSession(plan: WorkoutPlan, now: Long = System.currentTimeMillis()): Long {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "INSERT INTO sessions(plan_id, plan_name, started_at) VALUES(?,?,?)",
            arrayOf(plan.id, plan.name, now)
        )
        return db.rawQuery("SELECT last_insert_rowid()", null).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
    }

    fun lastSet(exerciseId: String): PreviousSet? {
        val db = dbHelper.readableDatabase
        return db.rawQuery(
            "SELECT weight, reps FROM sets WHERE exercise_id=? ORDER BY created_at DESC, id DESC LIMIT 1",
            arrayOf(exerciseId)
        ).use { c ->
            if (!c.moveToFirst()) null
            else PreviousSet(c.getDouble(0), c.getInt(1))
        }
    }

    private fun priorBest(exerciseId: String): Pair<Double, Double> {
        val db = dbHelper.readableDatabase
        return db.rawQuery(
            "SELECT COALESCE(MAX(weight),0), COALESCE(MAX(e1rm),0) FROM sets WHERE exercise_id=?",
            arrayOf(exerciseId)
        ).use { c ->
            c.moveToFirst()
            c.getDouble(0) to c.getDouble(1)
        }
    }

    fun saveSet(
        sessionId: Long,
        target: SetTarget,
        weight: Double,
        reps: Int,
        now: Long = System.currentTimeMillis(),
    ): LoggedSet {
        val e1rm = estimateOneRepMax(weight, reps)
        val (priorMaxWeight, priorMaxE1rm) = priorBest(target.exercise.id)
        val isPr = weight > priorMaxWeight || e1rm > priorMaxE1rm

        val db = dbHelper.writableDatabase
        db.execSQL(
            """
            INSERT INTO sets(
                session_id, exercise_id, exercise_name, display_set,
                weight, reps, e1rm, created_at, is_pr
            ) VALUES(?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                sessionId,
                target.exercise.id,
                target.exercise.name,
                target.displaySet,
                weight,
                reps,
                e1rm,
                now,
                if (isPr) 1 else 0,
            )
        )

        val id = db.rawQuery("SELECT last_insert_rowid()", null).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }

        return LoggedSet(
            id = id,
            sessionId = sessionId,
            exerciseId = target.exercise.id,
            exerciseName = target.exercise.name,
            displaySet = target.displaySet,
            weight = weight,
            reps = reps,
            e1rm = e1rm,
            timestamp = now,
            isPr = isPr,
        )
    }

    fun completeSession(
        sessionId: Long,
        planName: String,
        startedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): SessionSummary {
        val db = dbHelper.writableDatabase
        val aggregate = db.rawQuery(
            """
            SELECT
                COUNT(*),
                COALESCE(SUM(reps),0),
                COALESCE(SUM(weight * reps),0),
                COALESCE(SUM(is_pr),0)
            FROM sets WHERE session_id=?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { c ->
            c.moveToFirst()
            listOf(
                c.getInt(0).toDouble(),
                c.getInt(1).toDouble(),
                c.getDouble(2),
                c.getInt(3).toDouble(),
            )
        }

        val duration = max(0L, now - startedAt)
        val totalSets = aggregate[0].toInt()
        val totalReps = aggregate[1].toInt()
        val totalVolume = aggregate[2]
        val prCount = aggregate[3].toInt()

        db.execSQL(
            """
            UPDATE sessions
            SET completed_at=?, total_volume=?, total_sets=?, total_reps=?, duration_ms=?, pr_count=?
            WHERE id=?
            """.trimIndent(),
            arrayOf(now, totalVolume, totalSets, totalReps, duration, prCount, sessionId)
        )

        return SessionSummary(
            sessionId = sessionId,
            planName = planName,
            totalSets = totalSets,
            totalReps = totalReps,
            totalVolume = totalVolume,
            durationMillis = duration,
            prCount = prCount,
        )
    }

    fun abandonSession(sessionId: Long) {
        val db = dbHelper.writableDatabase
        db.delete("sets", "session_id=?", arrayOf(sessionId.toString()))
        db.delete("sessions", "id=?", arrayOf(sessionId.toString()))
    }

    fun analytics(now: Long = System.currentTimeMillis()): AnalyticsSnapshot {
        val db = dbHelper.readableDatabase

        val totals = db.rawQuery(
            """
            SELECT
                COUNT(*),
                COALESCE(SUM(total_sets),0),
                COALESCE(SUM(total_reps),0),
                COALESCE(SUM(total_volume),0),
                COALESCE(SUM(pr_count),0)
            FROM sessions
            WHERE completed_at IS NOT NULL
            """.trimIndent(),
            null
        ).use { c ->
            c.moveToFirst()
            listOf(
                c.getInt(0).toDouble(),
                c.getInt(1).toDouble(),
                c.getInt(2).toDouble(),
                c.getDouble(3),
                c.getInt(4).toDouble(),
            )
        }

        val bestE1rm = db.rawQuery("SELECT COALESCE(MAX(e1rm),0) FROM sets", null).use { c ->
            c.moveToFirst()
            c.getDouble(0)
        }

        val exercises = mutableListOf<ExerciseAnalytics>()
        db.rawQuery(
            """
            SELECT
                exercise_id,
                exercise_name,
                MAX(weight),
                MAX(e1rm),
                SUM(weight * reps),
                COUNT(*)
            FROM sets
            GROUP BY exercise_id, exercise_name
            ORDER BY exercise_name
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val exerciseId = c.getString(0)
                val exerciseName = c.getString(1)
                val maxWeight = c.getDouble(2)
                val best = c.getDouble(3)
                val volume = c.getDouble(4)
                val setCount = c.getInt(5)

                var lastWeight: Double? = null
                var lastReps: Int? = null
                db.rawQuery(
                    "SELECT weight,reps FROM sets WHERE exercise_id=? ORDER BY created_at DESC,id DESC LIMIT 1",
                    arrayOf(exerciseId)
                ).use { lc ->
                    if (lc.moveToFirst()) {
                        lastWeight = lc.getDouble(0)
                        lastReps = lc.getInt(1)
                    }
                }

                val recentE1rm = mutableListOf<Double>()
                db.rawQuery(
                    "SELECT e1rm FROM sets WHERE exercise_id=? ORDER BY created_at DESC,id DESC LIMIT 6",
                    arrayOf(exerciseId)
                ).use { hc ->
                    while (hc.moveToNext()) recentE1rm += hc.getDouble(0)
                }

                val trend = if (recentE1rm.size >= 6) {
                    val recent = recentE1rm.take(3).average()
                    val prior = recentE1rm.drop(3).take(3).average()
                    if (prior > 0) ((recent - prior) / prior) * 100.0 else null
                } else null

                exercises += ExerciseAnalytics(
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    maxWeight = maxWeight,
                    bestE1rm = best,
                    totalVolume = volume,
                    totalSets = setCount,
                    lastWeight = lastWeight,
                    lastReps = lastReps,
                    recentTrendPercent = trend,
                )
            }
        }

        val weekMillis = 7L * 24L * 60L * 60L * 1000L
        val weekly = (7 downTo 0).map { weeksAgo ->
            val start = now - (weeksAgo + 1L) * weekMillis
            val end = now - weeksAgo * weekMillis
            val volume = db.rawQuery(
                """
                SELECT COALESCE(SUM(weight * reps),0)
                FROM sets
                WHERE created_at>=? AND created_at<?
                """.trimIndent(),
                arrayOf(start.toString(), end.toString())
            ).use { c ->
                c.moveToFirst()
                c.getDouble(0)
            }
            WeeklyVolume(
                label = if (weeksAgo == 0) "Now" else "-${weeksAgo}w",
                volume = volume,
            )
        }

        val recent = mutableListOf<RecentSession>()
        db.rawQuery(
            """
            SELECT id, plan_name, completed_at, total_volume, total_sets, duration_ms, pr_count
            FROM sessions
            WHERE completed_at IS NOT NULL
            ORDER BY completed_at DESC
            LIMIT 8
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                recent += RecentSession(
                    id = c.getLong(0),
                    planName = c.getString(1),
                    completedAt = c.getLong(2),
                    totalVolume = c.getDouble(3),
                    totalSets = c.getInt(4),
                    durationMillis = c.getLong(5),
                    prCount = c.getInt(6),
                )
            }
        }

        return AnalyticsSnapshot(
            sessionCount = totals[0].toInt(),
            totalSets = totals[1].toInt(),
            totalReps = totals[2].toInt(),
            totalVolume = totals[3],
            totalPrs = totals[4].toInt(),
            bestE1rm = bestE1rm,
            exercises = exercises,
            weeklyVolume = weekly,
            recentSessions = recent,
        )
    }

    companion object {
        fun estimateOneRepMax(weight: Double, reps: Int): Double {
            if (weight <= 0 || reps <= 0) return 0.0
            return weight * (1.0 + reps / 30.0)
        }
    }
}
