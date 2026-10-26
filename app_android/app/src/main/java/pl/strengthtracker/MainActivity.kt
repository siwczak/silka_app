package pl.strengthtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF246B5B)
private val Ink = Color(0xFF202824)
private val Paper = Color(0xFFF6F7F4)

private fun decimalInput(value: String): String {
    var hasSeparator = false
    return buildString {
        value.forEach { character ->
            when {
                character in '0'..'9' -> append(character)
                (character == '.' || character == ',') && !hasSeparator -> {
                    append(character)
                    hasSeparator = true
                }
            }
        }
    }
}

private data class AppSnapshot(
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val weights: List<WeightEntry>,
    val records: List<PersonalRecord>,
    val selectedExerciseId: Int?,
    val progress: List<ProgressPoint>,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StrengthTrackerApp() }
    }
}

@Composable
private fun StrengthTrackerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val database = remember { LocalDatabase(context.applicationContext) }
    var tab by remember { mutableStateOf(0) }
    var exercises by remember { mutableStateOf(emptyList<Exercise>()) }
    var workouts by remember { mutableStateOf(emptyList<Workout>()) }
    var weights by remember { mutableStateOf(emptyList<WeightEntry>()) }
    var records by remember { mutableStateOf(emptyList<PersonalRecord>()) }
    var selectedExerciseId by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableStateOf(emptyList<ProgressPoint>()) }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var workoutSets by remember { mutableStateOf(emptyList<DraftSet>()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            val exerciseList = database.exercises()
            val progressId = selectedExerciseId?.takeIf { id -> exerciseList.any { it.id == id } }
                ?: exerciseList.firstOrNull()?.id
            AppSnapshot(
                exercises = exerciseList,
                workouts = database.workouts(),
                weights = database.bodyweight(),
                records = database.personalRecords(),
                selectedExerciseId = progressId,
                progress = progressId?.let(database::progress).orEmpty(),
            )
        }
        exercises = loaded.exercises
        workouts = loaded.workouts
        weights = loaded.weights
        records = loaded.records
        selectedExerciseId = loaded.selectedExerciseId
        progress = loaded.progress
    }

    fun selectProgressExercise(exerciseId: Int) {
        selectedExerciseId = exerciseId
        scope.launch {
            progress = withContext(Dispatchers.IO) { database.progress(exerciseId) }
        }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                action()
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "Nie udało się zapisać danych")
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(database) { reload() }
    DisposableEffect(database) { onDispose { database.close() } }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest,
            onPrimary = Color.White,
            secondary = Color(0xFFB35D3C),
            background = Paper,
            surface = Color.White,
            onSurface = Ink,
        ),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { androidx.compose.material3.Icon(Icons.Default.FitnessCenter, null) },
                        label = { Text("Trening") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { androidx.compose.material3.Icon(Icons.Default.History, null) },
                        label = { Text("Historia") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { androidx.compose.material3.Icon(Icons.Default.ShowChart, null) },
                        label = { Text("Postępy") },
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { androidx.compose.material3.Icon(Icons.Default.List, null) },
                        label = { Text("Ćwiczenia") },
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        icon = { androidx.compose.material3.Icon(Icons.Default.MonitorWeight, null) },
                        label = { Text("Masa ciała") },
                    )
                }
            },
            containerColor = Paper,
        ) { insets ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Strength Tracker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Dane zapisują się lokalnie na tym urządzeniu",
                    style = MaterialTheme.typography.bodySmall,
                    color = Forest,
                )

                when (tab) {
                    0 -> WorkoutScreen(
                        exercises = exercises,
                        busy = busy,
                        editingWorkout = editingWorkout,
                        sets = workoutSets,
                        onSetsChange = { workoutSets = it },
                        onCancelEdit = { editingWorkout = null; workoutSets = emptyList() },
                        onSave = { workoutId, date, notes, sets ->
                            perform {
                                withContext(Dispatchers.IO) {
                                    if (workoutId == null) database.saveWorkout(date, notes, sets)
                                    else database.updateWorkout(workoutId, date, notes, sets)
                                }
                                workoutSets = emptyList()
                                reload()
                                editingWorkout = null
                                snackbar.showSnackbar(if (workoutId == null) "Trening zapisany na tym urządzeniu" else "Trening zaktualizowany")
                            }
                        },
                    )
                    1 -> HistoryScreen(
                        workouts = workouts,
                        busy = busy,
                        onEdit = { workout ->
                            editingWorkout = workout
                            workoutSets = workout.sets.map { DraftSet(it.exerciseId, it.reps, it.weight) }
                            tab = 0
                        },
                        onDelete = { workout ->
                            perform {
                                withContext(Dispatchers.IO) { database.deleteWorkout(workout.id) }
                                reload()
                                snackbar.showSnackbar("Trening usunięty")
                            }
                        },
                    )
                    2 -> ProgressScreen(
                        exercises = exercises,
                        selectedExerciseId = selectedExerciseId,
                        progress = progress,
                        records = records,
                        onSelectExercise = ::selectProgressExercise,
                    )
                    3 -> ExerciseScreen(
                        exercises = exercises,
                        busy = busy,
                        onAdd = { name, category ->
                            perform {
                                val added = withContext(Dispatchers.IO) { database.addExercise(name, category) }
                                if (!added) {
                                    snackbar.showSnackbar("Ćwiczenie o tej nazwie już istnieje")
                                } else {
                                    reload()
                                    snackbar.showSnackbar("Ćwiczenie dodane")
                                }
                            }
                        },
                        onDelete = { exercise ->
                            perform {
                                val deleted = withContext(Dispatchers.IO) { database.deleteExercise(exercise.id) }
                                if (!deleted) {
                                    snackbar.showSnackbar("Nie można usunąć ćwiczenia użytego w treningach")
                                } else {
                                    reload()
                                }
                            }
                        },
                    )
                    else -> BodyweightScreen(
                        entries = weights,
                        busy = busy,
                        onSave = { date, weight ->
                            perform {
                                withContext(Dispatchers.IO) { database.saveBodyweight(date, weight) }
                                reload()
                                snackbar.showSnackbar("Pomiar zapisany")
                            }
                        },
                        onDelete = { entry ->
                            perform {
                                withContext(Dispatchers.IO) { database.deleteBodyweight(entry.id) }
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutScreen(
    exercises: List<Exercise>,
    busy: Boolean,
    editingWorkout: Workout?,
    sets: List<DraftSet>,
    onSetsChange: (List<DraftSet>) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (Int?, String, String, List<DraftSet>) -> Unit,
) {
    var date by remember(editingWorkout?.id) { mutableStateOf(editingWorkout?.date ?: today()) }
    var notes by remember(editingWorkout?.id) { mutableStateOf(editingWorkout?.notes ?: "") }
    var selectedExercise by remember(exercises, editingWorkout?.id) {
        mutableStateOf(
            editingWorkout?.sets?.firstOrNull()?.let { set -> exercises.firstOrNull { it.id == set.exerciseId } }
                ?: exercises.firstOrNull(),
        )
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(if (editingWorkout == null) "Nowy trening" else "Edytuj trening", "Zapisuj serie na bieżąco")
        OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Data (RRRR-MM-DD)") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
            OutlinedTextField(
                value = selectedExercise?.name ?: "Brak ćwiczeń",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text("Ćwiczenie") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded) },
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                exercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = { selectedExercise = exercise; menuExpanded = false },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it.filter { character -> character in '0'..'9' } },
                modifier = Modifier.weight(1f),
                label = { Text("Powtórzenia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = decimalInput(it) },
                modifier = Modifier.weight(1f),
                label = { Text("Ciężar (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        OutlinedButton(
            onClick = {
                val parsedReps = reps.toIntOrNull()
                val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
                if (selectedExercise != null && parsedReps != null && parsedReps > 0 && parsedWeight != null && parsedWeight >= 0) {
                    onSetsChange(sets + DraftSet(selectedExercise!!.id, parsedReps, parsedWeight))
                    reps = ""
                    weight = ""
                }
            },
            enabled = selectedExercise != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Dodaj serię") }

        if (sets.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sets.withIndex().groupBy { it.value.exerciseId }.forEach { (exerciseId, exerciseSets) ->
                        val name = exercises.firstOrNull { it.id == exerciseId }?.name ?: "Ćwiczenie"
                        Text("$name · Serie: ${exerciseSets.size}", fontWeight = FontWeight.SemiBold)
                        exerciseSets.forEachIndexed { setNumber, indexedSet ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${setNumber + 1}. ${indexedSet.value.reps} x ${indexedSet.value.weight} kg",
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        onSetsChange(sets.filterIndexed { index, _ -> index != indexedSet.index })
                                    },
                                ) { Text("Usuń") }
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notatka (opcjonalnie)") }, minLines = 2)
        Button(
            onClick = {
                if (sets.isNotEmpty()) {
                    onSave(editingWorkout?.id, date, notes, sets)
                    notes = ""
                    date = today()
                }
            },
            enabled = sets.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Zapisywanie..." else if (editingWorkout == null) "Zapisz trening" else "Zapisz zmiany") }
        if (editingWorkout != null) {
            TextButton(onClick = onCancelEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anuluj edycję") }
        }
    }
}

@Composable
private fun HistoryScreen(workouts: List<Workout>, busy: Boolean, onEdit: (Workout) -> Unit, onDelete: (Workout) -> Unit) {
    var pendingDelete by remember { mutableStateOf<Workout?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Historia", "${workouts.size} treningów zapisanych lokalnie")
        if (workouts.isEmpty()) Text("Brak treningów do wyświetlenia.")
        workouts.forEach { workout ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(workout.date, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        androidx.compose.material3.IconButton(onClick = { onEdit(workout) }, enabled = !busy) {
                            androidx.compose.material3.Icon(Icons.Default.Edit, contentDescription = "Edytuj trening", tint = Forest)
                        }
                        androidx.compose.material3.IconButton(onClick = { pendingDelete = workout }, enabled = !busy) {
                            androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = "Usuń trening", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    workout.sets.groupBy { it.exerciseId }.forEach { (_, exerciseSets) ->
                        val setSummary = exerciseSets.joinToString(", ") { "${it.reps}x${it.weight}kg" }
                        Text("${exerciseSets.first().exerciseName}  $setSummary")
                    }
                    if (workout.notes.isNotBlank()) Text(workout.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Usunąć trening?") },
            text = { Text("Trening z dnia ${workout.date} zostanie usunięty.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(workout) }) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Anuluj") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressScreen(
    exercises: List<Exercise>,
    selectedExerciseId: Int?,
    progress: List<ProgressPoint>,
    records: List<PersonalRecord>,
    onSelectExercise: (Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Postępy", "Najcięższa seria w kolejnych treningach")
        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
            OutlinedTextField(
                value = selectedExercise?.name ?: "Brak ćwiczeń",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text("Ćwiczenie") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded) },
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                exercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = { onSelectExercise(exercise.id); menuExpanded = false },
                    )
                }
            }
        }
        if (progress.isNotEmpty()) {
            LineChart(progress, Modifier.fillMaxWidth().height(190.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.first().date, style = MaterialTheme.typography.labelSmall)
                Text("${progress.maxOf { it.weight }} kg", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Text(progress.last().date, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text("Zapisz trening, aby zobaczyć wykres postępu.")
        }

        Spacer(Modifier.height(4.dp))
        SectionTitle("Rekordy szacowane", "1RM według wzoru Epleya")
        if (records.isEmpty()) Text("Rekordy pojawią się po zapisaniu treningu.")
        records.forEach { record ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(record.exerciseName, fontWeight = FontWeight.SemiBold)
                        Text("${record.weight} kg x ${record.reps}  ·  ${record.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${"%.1f".format(Locale.US, record.estimatedOneRepMax)} kg", color = Forest, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(Color(0xFFE3E7E2), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.weight }
        val minimum = values.minOrNull() ?: return@Canvas
        val spread = (values.maxOrNull() ?: minimum) - minimum
        val range = if (spread < 0.1) 1.0 else spread
        val coordinates = points.mapIndexed { index, point ->
            val x = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)
            val y = bottom - ((point.weight - minimum) / range).toFloat() * (bottom - top)
            Offset(x, y)
        }
        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(Forest, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        coordinates.forEach { point -> drawCircle(Forest, radius = 4.dp.toPx(), center = point) }
    }
}

@Composable
private fun ExerciseScreen(
    exercises: List<Exercise>,
    busy: Boolean,
    onAdd: (String, String) -> Unit,
    onDelete: (Exercise) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Ćwiczenia", "Katalog zapisany lokalnie")
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nazwa ćwiczenia") }, singleLine = true)
        OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Kategoria") }, singleLine = true)
        Button(
            onClick = {
                onAdd(name.trim(), category.trim())
                name = ""
                category = ""
            },
            enabled = name.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Dodaj ćwiczenie") }
        Spacer(Modifier.height(4.dp))
        exercises.forEach { exercise ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(exercise.name, fontWeight = FontWeight.SemiBold)
                        Text(exercise.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.IconButton(onClick = { pendingDelete = exercise }, enabled = !busy) {
                        androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = "Usuń ćwiczenie", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Usunąć ćwiczenie?") },
            text = { Text("Ćwiczenie można usunąć tylko wtedy, gdy nie zostało użyte w treningach.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(exercise) }) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun BodyweightScreen(
    entries: List<WeightEntry>,
    busy: Boolean,
    onSave: (String, Double) -> Unit,
    onDelete: (WeightEntry) -> Unit,
) {
    var date by remember { mutableStateOf(today()) }
    var weight by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Masa ciała", "Pomiary przechowywane na tym urządzeniu")
        OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Data (RRRR-MM-DD)") }, singleLine = true)
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = decimalInput(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Masa (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            onClick = {
                val parsed = weight.replace(',', '.').toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    onSave(date, parsed)
                    weight = ""
                    date = today()
                }
            },
            enabled = weight.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zapisz pomiar") }
        Spacer(Modifier.height(4.dp))
        if (entries.size > 1) {
            SectionTitle("Trend masy ciała", "${entries.last().date} – ${entries.first().date}")
            LineChart(
                entries.sortedBy { it.date }.map { ProgressPoint(it.date, it.weight) },
                Modifier.fillMaxWidth().height(170.dp),
            )
        }
        Text("Ostatnie pomiary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (entries.isEmpty()) Text("Brak pomiarów.")
        entries.forEach { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.date, modifier = Modifier.weight(1f))
                    Text("${entry.weight} kg", fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.IconButton(onClick = { onDelete(entry) }, enabled = !busy) {
                        androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = "Usuń pomiar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
