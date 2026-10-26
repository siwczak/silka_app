package pl.strengthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class Exercise(val id: Int, val name: String, val category: String)
internal data class DraftSet(val exerciseId: Int, val reps: Int, val weight: Double)
internal data class WorkoutSet(val exerciseId: Int, val exerciseName: String, val reps: Int, val weight: Double)
internal data class Workout(val id: Int, val date: String, val notes: String, val sets: List<WorkoutSet>)
internal data class WeightEntry(val id: Int, val date: String, val weight: Double)
internal data class ProgressPoint(val date: String, val weight: Double)
internal data class PersonalRecord(val exerciseName: String, val weight: Double, val reps: Int, val date: String, val estimatedOneRepMax: Double)

internal class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "strength_tracker.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exercises (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, category TEXT NOT NULL DEFAULT 'inne')")
        db.execSQL("CREATE TABLE workouts (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL REFERENCES workouts(id) ON DELETE CASCADE, exercise_id INTEGER NOT NULL REFERENCES exercises(id), set_number INTEGER NOT NULL, reps INTEGER NOT NULL, weight REAL NOT NULL)")
        db.execSQL("CREATE TABLE bodyweight (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL UNIQUE, weight REAL NOT NULL)")

        val defaults = listOf(
            "Przysiad ze sztangą" to "nogi",
            "Martwy ciąg" to "plecy",
            "Wyciskanie sztangi leżąc" to "klatka",
            "Wyciskanie żołnierskie (OHP)" to "barki",
            "Podciąganie" to "plecy",
            "Wiosłowanie sztangą" to "plecy",
        )
        defaults.forEach { (name, category) ->
            db.insertOrThrow("exercises", null, ContentValues().apply {
                put("name", name)
                put("category", category)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun exercises(): List<Exercise> = readableDatabase.rawQuery(
        "SELECT id, name, category FROM exercises ORDER BY name", null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(Exercise(cursor.getInt(0), cursor.getString(1), cursor.getString(2)))
            }
        }
    }

    fun addExercise(name: String, category: String): Boolean {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim().ifBlank { "inne" })
        }
        return writableDatabase.insertWithOnConflict(
            "exercises", null, values, SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    fun deleteExercise(id: Int): Boolean {
        val db = writableDatabase
        val used = db.rawQuery("SELECT COUNT(*) FROM sets WHERE exercise_id = ?", arrayOf(id.toString())).use {
            it.moveToFirst()
            it.getInt(0) > 0
        }
        if (used) return false
        return db.delete("exercises", "id = ?", arrayOf(id.toString())) > 0
    }

    fun saveWorkout(date: String, notes: String, sets: List<DraftSet>) {
        require(sets.isNotEmpty()) { "Dodaj przynajmniej jedną serię" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val workoutId = db.insertOrThrow("workouts", null, ContentValues().apply {
                put("date", date)
                put("notes", notes)
            })
            val exerciseSetNumbers = mutableMapOf<Int, Int>()
            sets.forEach { set ->
                val setNumber = (exerciseSetNumbers[set.exerciseId] ?: 0) + 1
                exerciseSetNumbers[set.exerciseId] = setNumber
                db.insertOrThrow("sets", null, ContentValues().apply {
                    put("workout_id", workoutId)
                    put("exercise_id", set.exerciseId)
                    put("set_number", setNumber)
                    put("reps", set.reps)
                    put("weight", set.weight)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun workouts(): List<Workout> {
        val db = readableDatabase
        return db.rawQuery("SELECT id, date, notes FROM workouts ORDER BY date DESC, id DESC", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    val sets = db.rawQuery(
                        "SELECT e.id, e.name, s.reps, s.weight FROM sets s JOIN exercises e ON e.id = s.exercise_id WHERE s.workout_id = ? ORDER BY e.name, s.set_number",
                        arrayOf(id.toString()),
                    ).use { setCursor ->
                        buildList {
                            while (setCursor.moveToNext()) {
                                add(WorkoutSet(setCursor.getInt(0), setCursor.getString(1), setCursor.getInt(2), setCursor.getDouble(3)))
                            }
                        }
                    }
                    add(Workout(id, cursor.getString(1), cursor.getString(2), sets))
                }
            }
        }
    }

    fun deleteWorkout(id: Int) {
        writableDatabase.delete("workouts", "id = ?", arrayOf(id.toString()))
    }

    fun updateWorkout(id: Int, date: String, notes: String, sets: List<DraftSet>) {
        require(sets.isNotEmpty()) { "Dodaj przynajmniej jedną serię" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("workouts", ContentValues().apply {
                put("date", date)
                put("notes", notes)
            }, "id = ?", arrayOf(id.toString()))
            db.delete("sets", "workout_id = ?", arrayOf(id.toString()))
            val exerciseSetNumbers = mutableMapOf<Int, Int>()
            sets.forEach { set ->
                val setNumber = (exerciseSetNumbers[set.exerciseId] ?: 0) + 1
                exerciseSetNumbers[set.exerciseId] = setNumber
                db.insertOrThrow("sets", null, ContentValues().apply {
                    put("workout_id", id)
                    put("exercise_id", set.exerciseId)
                    put("set_number", setNumber)
                    put("reps", set.reps)
                    put("weight", set.weight)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun progress(exerciseId: Int): List<ProgressPoint> = readableDatabase.rawQuery(
        "SELECT w.date, MAX(s.weight) FROM sets s JOIN workouts w ON w.id = s.workout_id WHERE s.exercise_id = ? GROUP BY w.date ORDER BY w.date",
        arrayOf(exerciseId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(ProgressPoint(cursor.getString(0), cursor.getDouble(1)))
        }
    }

    fun personalRecords(): List<PersonalRecord> {
        val records = linkedMapOf<Int, PersonalRecord>()
        readableDatabase.rawQuery(
            "SELECT e.id, e.name, s.weight, s.reps, w.date FROM sets s JOIN exercises e ON e.id = s.exercise_id JOIN workouts w ON w.id = s.workout_id ORDER BY (s.weight * (1 + s.reps / 30.0)) DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getInt(0)
                if (exerciseId !in records) {
                    val weight = cursor.getDouble(2)
                    val reps = cursor.getInt(3)
                    records[exerciseId] = PersonalRecord(
                        exerciseName = cursor.getString(1),
                        weight = weight,
                        reps = reps,
                        date = cursor.getString(4),
                        estimatedOneRepMax = weight * (1 + reps / 30.0),
                    )
                }
            }
        }
        return records.values.toList()
    }

    fun saveBodyweight(date: String, weight: Double) {
        writableDatabase.insertWithOnConflict(
            "bodyweight",
            null,
            ContentValues().apply {
                put("date", date)
                put("weight", weight)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun bodyweight(): List<WeightEntry> = readableDatabase.rawQuery(
        "SELECT id, date, weight FROM bodyweight ORDER BY date DESC LIMIT 30", null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(WeightEntry(cursor.getInt(0), cursor.getString(1), cursor.getDouble(2)))
            }
        }
    }

    fun deleteBodyweight(id: Int) {
        writableDatabase.delete("bodyweight", "id = ?", arrayOf(id.toString()))
    }
}
