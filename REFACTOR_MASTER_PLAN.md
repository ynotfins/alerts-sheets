# AlertsToSheets - Comprehensive Refactor Master Plan

**Date:** December 17, 2025  
**Goal:** Transform the current prototype into a production-grade, maintainable, and scalable Android application following modern best practices.

---

## Executive Summary

### Current State Assessment

**✅ What Works Well:**
- Core notification interception functionality
- BNN parsing logic (recently stabilized)
- Queue-based offline persistence
- Google Sheets integration
- Multi-endpoint support
- Basic deduplication

**❌ Critical Issues:**
- **No MVVM Architecture**: Activities contain business logic and data access
- **No Dependency Injection**: Tight coupling, hard-to-test singletons
- **SharedPreferences Overuse**: Complex data structures serialized manually
- **GlobalScope Usage**: Deprecated coroutine scope, no lifecycle awareness
- **No Repository Pattern**: Data access scattered across services/activities
- **No Testing**: Zero unit tests, integration tests, or UI tests
- **Mixed Concerns**: UI, business logic, and data layer all intertwined
- **Poor Error Handling**: Try-catch blocks with silent failures
- **No Analytics/Monitoring**: No crash reporting or performance tracking
- **Hardcoded Values**: Configuration spread across multiple files

---

## Phase 1: Foundation & Architecture (Days 1-3)

### 1.1 Setup Modern Build System

**Current Issues:**
- Kapt commented out (Room removed due to build issues)
- No build variants (debug/release/staging)
- No ProGuard/R8 optimization
- ViewBinding enabled but inconsistently used

**Changes:**
```gradle
// build.gradle (app)
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.devtools.ksp' version '1.9.20-1.0.14' // Replace kapt
    id 'kotlin-parcelize'
    id 'com.google.dagger.hilt.android' // Add Hilt
}

android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.example.alertsheets"
        minSdk 26
        targetSdk 34
        versionCode 2
        versionName "2.0.0"
        
        // Build config fields
        buildConfigField "String", "SHEET_BASE_URL", "\"https://script.google.com\""
        buildConfigField "boolean", "DEBUG_LOGGING", "true"
    }
    
    buildTypes {
        debug {
            applicationIdSuffix ".debug"
            debuggable true
            minifyEnabled false
        }
        
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            
            // Disable logging in production
            buildConfigField "boolean", "DEBUG_LOGGING", "false"
        }
        
        staging {
            initWith debug
            applicationIdSuffix ".staging"
        }
    }
    
    buildFeatures {
        viewBinding true
        buildConfig true
    }
    
    kotlinOptions {
        jvmTarget = '17'
        freeCompilerArgs += [
            '-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi',
            '-opt-in=kotlinx.coroutines.FlowPreview'
        ]
    }
}

dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.13.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Lifecycle & ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
    implementation 'androidx.activity:activity-ktx:1.8.2'
    implementation 'androidx.fragment:fragment-ktx:1.6.2'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    
    // Room Database (FIX build issues with KSP)
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    ksp 'androidx.room:room-compiler:2.6.1'
    
    // Dependency Injection (Hilt)
    implementation 'com.google.dagger:hilt-android:2.50'
    ksp 'com.google.dagger:hilt-compiler:2.50'
    
    // Networking
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    
    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // WorkManager (Replace manual queue processing)
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    
    // Datastore (Replace SharedPreferences)
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
    
    // Timber (Replace Log.*)
    implementation 'com.jakewharton.timber:timber:5.0.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito.kotlin:mockito-kotlin:5.2.1'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
    testImplementation 'androidx.arch.core:core-testing:2.2.0'
    testImplementation 'app.cash.turbine:turbine:1.0.0' // Flow testing
    
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation 'androidx.work:work-testing:2.9.0'
}
```

### 1.2 Implement Clean Architecture

**New Package Structure:**
```
com.example.alertsheets/
├── app/
│   ├── AlertsApp.kt                    # Application class (Hilt)
│   └── di/                              # Dependency Injection
│       ├── AppModule.kt
│       ├── DatabaseModule.kt
│       ├── NetworkModule.kt
│       └── RepositoryModule.kt
│
├── data/                                # DATA LAYER
│   ├── local/
│   │   ├── db/
│   │   │   ├── AlertsDatabase.kt       # Room database
│   │   │   ├── dao/
│   │   │   │   ├── LogEntryDao.kt
│   │   │   │   ├── QueueRequestDao.kt
│   │   │   │   └── EndpointDao.kt
│   │   │   └── entities/
│   │   │       ├── LogEntryEntity.kt
│   │   │       ├── QueueRequestEntity.kt
│   │   │       └── EndpointEntity.kt
│   │   └── preferences/
│   │       └── AppPreferences.kt       # DataStore wrapper
│   │
│   ├── remote/
│   │   ├── api/
│   │   │   └── GoogleSheetsApi.kt      # OkHttp client interface
│   │   └── dto/
│   │       └── ParsedDataDto.kt        # Network DTOs
│   │
│   └── repository/
│       ├── LogRepository.kt            # Clean implementation
│       ├── EndpointRepository.kt
│       ├── QueueRepository.kt
│       ├── PreferencesRepository.kt
│       └── NotificationRepository.kt
│
├── domain/                              # DOMAIN LAYER
│   ├── model/
│   │   ├── ParsedNotification.kt       # Domain models
│   │   ├── LogEntry.kt
│   │   ├── Endpoint.kt
│   │   └── AppConfig.kt
│   │
│   ├── usecase/
│   │   ├── notification/
│   │   │   ├── ProcessNotificationUseCase.kt
│   │   │   ├── ParseBnnNotificationUseCase.kt
│   │   │   └── EnqueueNotificationUseCase.kt
│   │   ├── log/
│   │   │   ├── GetLogsUseCase.kt
│   │   │   └── UpdateLogStatusUseCase.kt
│   │   ├── queue/
│   │   │   ├── ProcessQueueUseCase.kt
│   │   │   └── RetryFailedRequestsUseCase.kt
│   │   └── config/
│   │       ├── GetEndpointsUseCase.kt
│   │       └── SaveEndpointUseCase.kt
│   │
│   └── parser/
│       ├── BnnParser.kt                # Interface
│       ├── BnnParserImpl.kt
│       └── ParsingRules.kt
│
├── presentation/                        # PRESENTATION LAYER
│   ├── ui/
│   │   ├── main/
│   │   │   ├── MainActivity.kt
│   │   │   ├── MainViewModel.kt
│   │   │   └── MainState.kt
│   │   ├── logs/
│   │   │   ├── LogActivity.kt
│   │   │   ├── LogViewModel.kt
│   │   │   ├── LogAdapter.kt
│   │   │   └── LogState.kt
│   │   ├── apps/
│   │   │   ├── AppsListActivity.kt
│   │   │   ├── AppsViewModel.kt
│   │   │   └── AppsState.kt
│   │   ├── config/
│   │   │   ├── AppConfigActivity.kt
│   │   │   └── AppConfigViewModel.kt
│   │   ├── endpoints/
│   │   │   ├── EndpointActivity.kt
│   │   │   └── EndpointViewModel.kt
│   │   └── permissions/
│   │       ├── PermissionsActivity.kt
│   │       └── PermissionsViewModel.kt
│   │
│   └── service/
│       ├── NotificationListenerService.kt
│       └── NotificationProcessor.kt    # Extracted business logic
│
├── worker/                              # BACKGROUND TASKS
│   ├── QueueProcessorWorker.kt         # WorkManager
│   └── RetryWorker.kt
│
└── util/
    ├── Constants.kt
    ├── Extensions.kt
    ├── Result.kt                        # sealed class for error handling
    └── Logger.kt                        # Timber wrapper
```

### 1.3 Implement Dependency Injection (Hilt)

**AlertsApp.kt:**
```kotlin
@HiltAndroidApp
class AlertsApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber
        if (BuildConfig.DEBUG_LOGGING) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize WorkManager for queue processing
        WorkManager.getInstance(this)
    }
}
```

**AppModule.kt:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
    
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    
    @Provides
    @Singleton
    fun provideBnnParser(): BnnParser = BnnParserImpl()
    
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope = 
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

**DatabaseModule.kt:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAlertsDatabase(@ApplicationContext context: Context): AlertsDatabase {
        return Room.databaseBuilder(
            context,
            AlertsDatabase::class.java,
            "alerts_database"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration() // Only for dev
            .build()
    }
    
    @Provides
    fun provideLogEntryDao(database: AlertsDatabase) = database.logEntryDao()
    
    @Provides
    fun provideQueueRequestDao(database: AlertsDatabase) = database.queueRequestDao()
    
    @Provides
    fun provideEndpointDao(database: AlertsDatabase) = database.endpointDao()
}
```

---

## Phase 2: Data Layer Refactor (Days 4-5)

### 2.1 Replace SharedPreferences with Room + DataStore

**Why:**
- SharedPreferences is synchronous (blocks main thread)
- Manual JSON serialization is error-prone
- No type safety
- No migrations for schema changes

**Room Entities:**

```kotlin
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val packageName: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "status") val status: LogStatus,
    val rawJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "queue_requests")
data class QueueRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val payload: String,
    val status: String,
    val retryCount: Int = 0,
    val createdAt: Long,
    val logId: String,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "endpoints")
data class EndpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val lastSuccessAt: Long? = null
)

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val isEnabled: Boolean,
    val customTemplate: String?,
    val updatedAt: Long
)
```

**Room DAOs:**

```kotlin
@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<LogEntryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntryEntity)
    
    @Query("UPDATE log_entries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: LogStatus)
    
    @Query("DELETE FROM log_entries WHERE timestamp < :oldestTimestamp")
    suspend fun deleteOlderThan(oldestTimestamp: Long)
}

@Dao
interface QueueRequestDao {
    @Query("SELECT * FROM queue_requests WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingRequests(): Flow<List<QueueRequestEntity>>
    
    @Insert
    suspend fun insert(request: QueueRequestEntity): Long
    
    @Update
    suspend fun update(request: QueueRequestEntity)
    
    @Delete
    suspend fun delete(request: QueueRequestEntity)
    
    @Query("SELECT COUNT(*) FROM queue_requests WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
```

**DataStore for Simple Preferences:**

```kotlin
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    
    companion object {
        private val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        private val SHOULD_CLEAN_DATA = booleanPreferencesKey("should_clean_data")
        private val TARGET_APPS = stringSetPreferencesKey("target_apps")
    }
    
    val masterEnabled: Flow<Boolean> = dataStore.data
        .map { it[MASTER_ENABLED] ?: true }
    
    suspend fun setMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[MASTER_ENABLED] = enabled }
    }
    
    val targetApps: Flow<Set<String>> = dataStore.data
        .map { it[TARGET_APPS] ?: emptySet() }
    
    suspend fun setTargetApps(apps: Set<String>) {
        dataStore.edit { it[TARGET_APPS] = apps }
    }
}

private val Context.dataStore by preferencesDataStore(name = "app_preferences")
```

### 2.2 Implement Repository Pattern

**LogRepository (Clean):**

```kotlin
interface LogRepository {
    fun getRecentLogs(limit: Int = 200): Flow<List<LogEntry>>
    suspend fun addLog(entry: LogEntry)
    suspend fun updateLogStatus(id: String, status: LogStatus)
    suspend fun cleanup(keepDays: Int = 30)
}

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logEntryDao: LogEntryDao
) : LogRepository {
    
    override fun getRecentLogs(limit: Int): Flow<List<LogEntry>> {
        return logEntryDao.getRecentLogs(limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override suspend fun addLog(entry: LogEntry) {
        logEntryDao.insert(entry.toEntity())
    }
    
    override suspend fun updateLogStatus(id: String, status: LogStatus) {
        logEntryDao.updateStatus(id, status)
    }
    
    override suspend fun cleanup(keepDays: Int) {
        val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        logEntryDao.deleteOlderThan(cutoff)
    }
}
```

---

## Phase 3: Domain Layer & Use Cases (Days 6-7)

### 3.1 Create Use Cases for Business Logic

**ProcessNotificationUseCase:**

```kotlin
class ProcessNotificationUseCase @Inject constructor(
    private val parseBnnUseCase: ParseBnnNotificationUseCase,
    private val enqueueUseCase: EnqueueNotificationUseCase,
    private val logRepository: LogRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(
        packageName: String,
        title: String,
        text: String,
        bigText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Check master switch
            val masterEnabled = preferencesRepository.getMasterEnabled().first()
            if (!masterEnabled) {
                return@withContext Result.Success(Unit)
            }
            
            // 2. Check app filter
            val targetApps = preferencesRepository.getTargetApps().first()
            if (targetApps.isNotEmpty() && packageName !in targetApps) {
                logRepository.addLog(
                    LogEntry(
                        packageName = packageName,
                        title = title,
                        content = "App filtered out",
                        status = LogStatus.IGNORED
                    )
                )
                return@withContext Result.Success(Unit)
            }
            
            // 3. Build full content
            val fullContent = listOf(title, text, bigText)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            
            // 4. Detect BNN and parse
            val isBnn = packageName == "us.bnn.newsapp" ||
                        fullContent.contains("<C> BNN", ignoreCase = true)
            
            if (isBnn) {
                when (val parseResult = parseBnnUseCase(fullContent)) {
                    is Result.Success -> {
                        val parsed = parseResult.data
                        enqueueUseCase(parsed)
                        
                        logRepository.addLog(
                            LogEntry(
                                packageName = packageName,
                                title = title,
                                content = parsed.incidentDetails,
                                status = LogStatus.PENDING,
                                rawJson = Gson().toJson(parsed)
                            )
                        )
                    }
                    is Result.Error -> {
                        Timber.e("BNN Parse failed: ${parseResult.exception}")
                        // Fallback to generic
                    }
                }
            } else {
                // Generic notification handling
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error processing notification")
            Result.Error(e)
        }
    }
}
```

**ParseBnnNotificationUseCase:**

```kotlin
class ParseBnnNotificationUseCase @Inject constructor(
    private val parser: BnnParser
) {
    operator fun invoke(fullText: String): Result<ParsedNotification> {
        return try {
            val parsed = parser.parse(fullText)
            if (parsed != null) {
                Result.Success(parsed)
            } else {
                Result.Error(ParseException("Parser returned null"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
```

### 3.2 Extract Parser to Domain Layer

**BnnParser Interface:**

```kotlin
interface BnnParser {
    fun parse(fullText: String): ParsedNotification?
}

class BnnParserImpl @Inject constructor() : BnnParser {
    
    override fun parse(fullText: String): ParsedNotification? {
        // Same logic as current Parser.kt but:
        // 1. Better error handling (throw exceptions instead of silent null)
        // 2. Immutable results
        // 3. No Android dependencies (pure Kotlin)
        // 4. Unit testable
    }
}
```

---

## Phase 4: Presentation Layer (Days 8-10)

### 4.1 Implement MVVM with ViewModels

**MainViewModel:**

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val endpointRepository: EndpointRepository,
    private val queueRepository: QueueRepository,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()
    
    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.getMasterEnabled(),
                preferencesRepository.getTargetApps(),
                endpointRepository.getActiveEndpoints(),
                queueRepository.getPendingCount()
            ) { masterEnabled, targetApps, endpoints, queueCount ->
                MainState(
                    masterEnabled = masterEnabled,
                    selectedAppsCount = targetApps.size,
                    activeEndpointsCount = endpoints.size,
                    queuedRequestsCount = queueCount,
                    permissions = checkPermissionsUseCase()
                )
            }.collect {
                _state.value = it
            }
        }
    }
    
    fun toggleMaster() {
        viewModelScope.launch {
            val current = _state.value.masterEnabled
            preferencesRepository.setMasterEnabled(!current)
        }
    }
}

data class MainState(
    val masterEnabled: Boolean = true,
    val selectedAppsCount: Int = 0,
    val activeEndpointsCount: Int = 0,
    val queuedRequestsCount: Int = 0,
    val permissions: PermissionState = PermissionState()
)

data class PermissionState(
    val notificationListener: Boolean = false,
    val sms: Boolean = false,
    val batteryOptimization: Boolean = false
)
```

**MainActivity (Clean):**

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainDashboardBinding
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeState()
    }
    
    private fun setupUI() {
        binding.btnMaster.setOnClickListener {
            viewModel.toggleMaster()
        }
        
        binding.cardApps.setOnClickListener {
            startActivity(Intent(this, AppsListActivity::class.java))
        }
        
        // ... other click listeners
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    private fun updateUI(state: MainState) {
        // Update master button
        binding.btnMaster.text = if (state.masterEnabled) "LIVE" else "PAUSED"
        binding.btnMaster.setBackgroundColor(
            if (state.masterEnabled) Color.GREEN else Color.RED
        )
        
        // Update status dots
        binding.dotApps.setColorFilter(
            if (state.selectedAppsCount > 0) Color.GREEN else Color.YELLOW
        )
        
        // Update footer ticker
        binding.footerTicker.text = "Monitoring: ${state.selectedAppsCount} apps | " +
                                    "${state.queuedRequestsCount} queued"
    }
}
```

### 4.2 Implement StateFlow for Reactive UI

**LogViewModel:**

```kotlin
@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LogUiState>(LogUiState.Loading)
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()
    
    init {
        loadLogs()
    }
    
    private fun loadLogs() {
        viewModelScope.launch {
            logRepository.getRecentLogs()
                .catch { e ->
                    _uiState.value = LogUiState.Error(e.message ?: "Unknown error")
                }
                .collect { logs ->
                    _uiState.value = if (logs.isEmpty()) {
                        LogUiState.Empty
                    } else {
                        LogUiState.Success(logs)
                    }
                }
        }
    }
    
    fun retryFailedLog(logId: String) {
        viewModelScope.launch {
            // Trigger retry logic
        }
    }
}

sealed class LogUiState {
    object Loading : LogUiState()
    object Empty : LogUiState()
    data class Success(val logs: List<LogEntry>) : LogUiState()
    data class Error(val message: String) : LogUiState()
}
```

---

## Phase 5: Background Processing (Days 11-12)

### 5.1 Replace Manual Queue Processing with WorkManager

**Why WorkManager:**
- Battery-efficient
- Respects Doze mode
- Automatic retry with backoff
- Guaranteed execution
- Survives app kills and reboots

**QueueProcessorWorker:**

```kotlin
@HiltWorker
class QueueProcessorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queueRepository: QueueRepository,
    private val networkClient: NetworkClient,
    private val logRepository: LogRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val pending = queueRepository.getPendingRequests().first()
            
            if (pending.isEmpty()) {
                return Result.success()
            }
            
            var successCount = 0
            var failureCount = 0
            
            pending.forEach { request ->
                when (val result = networkClient.send(request.url, request.payload)) {
                    is NetworkResult.Success -> {
                        queueRepository.deleteRequest(request.id)
                        logRepository.updateLogStatus(request.logId, LogStatus.SENT)
                        successCount++
                    }
                    is NetworkResult.Error -> {
                        if (request.retryCount >= MAX_RETRIES) {
                            queueRepository.deleteRequest(request.id)
                            logRepository.updateLogStatus(request.logId, LogStatus.FAILED)
                        } else {
                            queueRepository.incrementRetryCount(request.id)
                        }
                        failureCount++
                    }
                }
                
                delay(200) // Rate limiting
            }
            
            Timber.i("Queue processed: $successCount success, $failureCount failures")
            
            if (failureCount > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Queue worker failed")
            Result.retry()
        }
    }
    
    companion object {
        private const val MAX_RETRIES = 10
        const val WORK_NAME = "queue_processor"
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<QueueProcessorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
```

### 5.2 Refactor NotificationService

**NotificationListenerService (Clean):**

```kotlin
@AndroidEntryPoint
class NotificationListenerService : NotificationListenerService() {
    
    @Inject lateinit var processor: NotificationProcessor
    @Inject lateinit var serviceScope: CoroutineScope
    
    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        
        serviceScope.launch {
            processor.process(sbn)
        }
    }
    
    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alerts Monitoring")
            .setContentText("Listening for notifications")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(FOREGROUND_ID, notification)
    }
    
    companion object {
        private const val CHANNEL_ID = "service_channel"
        private const val FOREGROUND_ID = 101
    }
}
```

**NotificationProcessor (Extracted Business Logic):**

```kotlin
@Singleton
class NotificationProcessor @Inject constructor(
    private val processNotificationUseCase: ProcessNotificationUseCase
) {
    suspend fun process(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        
        processNotificationUseCase(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText
        )
    }
}
```

---

## Phase 6: Error Handling & Monitoring (Days 13-14)

### 6.1 Implement Result Sealed Class

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (Exception) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}
```

### 6.2 Replace Log.* with Timber

**Why:**
- Automatic tagging
- Production-safe (no logs in release)
- Crash reporting integration
- Better formatting

**Setup:**

```kotlin
// In Application onCreate()
if (BuildConfig.DEBUG_LOGGING) {
    Timber.plant(Timber.DebugTree())
} else {
    // Plant production tree (e.g., Crashlytics)
    Timber.plant(CrashlyticsTree())
}

// Usage everywhere:
Timber.d("Notification received: ${sbn.packageName}")
Timber.e(exception, "Failed to parse BNN")
Timber.i("Queue processed: $successCount items")
```

### 6.3 Add Crash Reporting (Optional)

```gradle
// Firebase Crashlytics
implementation 'com.google.firebase:firebase-crashlytics-ktx:18.6.1'
```

```kotlin
class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.ERROR) {
            Firebase.crashlytics.log(message)
            t?.let { Firebase.crashlytics.recordException(it) }
        }
    }
}
```

---

## Phase 7: Testing Infrastructure (Days 15-17)

### 7.1 Unit Tests for Parsers

```kotlin
class BnnParserTest {
    
    private lateinit var parser: BnnParser
    
    @Before
    fun setup() {
        parser = BnnParserImpl()
    }
    
    @Test
    fun `parse standard format with update status`() {
        // Given
        val input = "U/D NJ| Bergen| Paramus| 123 Main St| Fire| Details | <C> BNN | E-1/L-1 | #1234567"
        
        // When
        val result = parser.parse(input)
        
        // Then
        assertNotNull(result)
        assertEquals("Update", result?.status)
        assertEquals("NJ", result?.state)
        assertEquals("Bergen", result?.county)
        assertEquals("Paramus", result?.city)
        assertEquals("123 Main St", result?.address)
        assertEquals("#1234567", result?.incidentId)
        assertEquals(listOf("E-1", "L-1"), result?.fdCodes)
    }
    
    @Test
    fun `parse with missing incident ID generates hash`() {
        // Given
        val input = "NJ| Bergen| Paramus| 123 Main St| Fire| Details | <C> BNN"
        
        // When
        val result = parser.parse(input)
        
        // Then
        assertNotNull(result)
        assertTrue(result?.incidentId?.startsWith("#") == true)
        assertEquals(8, result?.incidentId?.length) // # + 7 digits
    }
    
    @Test
    fun `parse filters out BNNDESK from FD codes`() {
        // Given
        val input = "NJ| Bergen| Paramus| 123 Main St| Fire| Details | <C> BNN | BNNDESK/E-1/L-1 | #1234567"
        
        // When
        val result = parser.parse(input)
        
        // Then
        assertEquals(listOf("E-1", "L-1"), result?.fdCodes)
        assertFalse(result?.fdCodes?.contains("BNNDESK") == true)
    }
}
```

### 7.2 ViewModel Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var viewModel: MainViewModel
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var endpointRepository: FakeEndpointRepository
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        preferencesRepository = FakePreferencesRepository()
        endpointRepository = FakeEndpointRepository()
        
        viewModel = MainViewModel(
            preferencesRepository,
            endpointRepository,
            FakeQueueRepository(),
            FakeCheckPermissionsUseCase()
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `toggleMaster updates state`() = runTest {
        // Given
        val initialState = viewModel.state.value
        assertTrue(initialState.masterEnabled)
        
        // When
        viewModel.toggleMaster()
        advanceUntilIdle()
        
        // Then
        assertFalse(viewModel.state.value.masterEnabled)
    }
}
```

### 7.3 Repository Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LogRepositoryTest {
    
    private lateinit var repository: LogRepository
    private lateinit var dao: FakeLogEntryDao
    
    @Before
    fun setup() {
        dao = FakeLogEntryDao()
        repository = LogRepositoryImpl(dao)
    }
    
    @Test
    fun `addLog inserts into database`() = runTest {
        // Given
        val entry = LogEntry(
            packageName = "com.test",
            title = "Test",
            content = "Content",
            status = LogStatus.PENDING
        )
        
        // When
        repository.addLog(entry)
        
        // Then
        val logs = repository.getRecentLogs().first()
        assertEquals(1, logs.size)
        assertEquals(entry.title, logs[0].title)
    }
}
```

### 7.4 Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class QueueProcessingIntegrationTest {
    
    @get:Rule
    val workManagerRule = WorkManagerTestRule()
    
    @Test
    fun queueProcessorWorker_processesRequests() {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = OneTimeWorkRequestBuilder<QueueProcessorWorker>().build()
        
        // When
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request).result.get()
        
        // Then
        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
    }
}
```

---

## Phase 8: UI/UX Improvements (Days 18-19)

### 8.1 Material Design 3

```gradle
implementation 'com.google.android.material:material:1.11.0'
```

**themes.xml:**

```xml
<resources>
    <style name="Theme.AlertsToSheets" parent="Theme.Material3.DayNight">
        <item name="colorPrimary">@color/md_theme_primary</item>
        <item name="colorOnPrimary">@color/md_theme_on_primary</item>
        <item name="colorPrimaryContainer">@color/md_theme_primary_container</item>
        
        <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
    </style>
</resources>
```

### 8.2 Improve Dashboard UI

- Replace ImageView dots with custom indicators
- Add FAB for quick actions
- Implement SwipeRefreshLayout for logs
- Add SearchView to apps list (already done)
- Show queue stats with progress bars

### 8.3 Add Empty States

```xml
<!-- Empty state for logs -->
<LinearLayout
    android:id="@+id/empty_state"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:visibility="gone">
    
    <ImageView
        android:layout_width="120dp"
        android:layout_height="120dp"
        android:src="@drawable/ic_empty_logs"
        android:tint="?attr/colorOnSurfaceVariant"/>
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="No logs yet"
        android:textAppearance="?attr/textAppearanceHeadlineSmall"/>
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Notifications will appear here once received"
        android:textAppearance="?attr/textAppearanceBodyMedium"/>
</LinearLayout>
```

---

## Phase 9: Performance Optimization (Days 20-21)

### 9.1 Database Indexes

```kotlin
@Entity(
    tableName = "log_entries",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["status"]),
        Index(value = ["packageName"])
    ]
)
data class LogEntryEntity(...)
```

### 9.2 Pagination for Large Lists

```kotlin
@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllLogsPaged(): PagingSource<Int, LogEntryEntity>
}

// ViewModel
val logs: Flow<PagingData<LogEntry>> = Pager(
    PagingConfig(pageSize = 50)
) {
    logEntryDao.getAllLogsPaged()
}.flow.cachedIn(viewModelScope)
```

### 9.3 Optimize Parser Performance

- Precompile regex patterns
- Cache street suffix list as Set
- Use StringBuilder for string concatenation
- Avoid unnecessary allocations

### 9.4 Memory Leak Prevention

- Use `viewLifecycleOwner` in Fragments
- Cancel coroutines in onDestroy
- Weak references for callbacks
- LeakCanary integration for debug builds

---

## Phase 10: Documentation & Deployment (Days 22-23)

### 10.1 Code Documentation

- KDoc for all public APIs
- Architecture decision records (ADRs)
- Update HANDOFF.md with new architecture
- Create TESTING.md guide

### 10.2 ProGuard Rules

```proguard
# Gson
-keep class com.example.alertsheets.domain.model.** { *; }
-keep class com.example.alertsheets.data.remote.dto.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
```

### 10.3 Release Build Checklist

- [ ] All tests passing
- [ ] ProGuard enabled and tested
- [ ] Crashlytics configured
- [ ] Version bumped to 2.0.0
- [ ] Release notes prepared
- [ ] APK signed with release keystore

---

## Migration Strategy

### Option A: Big Bang (Full Rewrite)
**Pros:** Clean slate, no technical debt  
**Cons:** High risk, long development time  
**Recommendation:** ❌ NOT recommended

### Option B: Strangler Pattern (Incremental)
**Pros:** Low risk, testable at each step  
**Cons:** Longer timeline, temporary duplication  
**Recommendation:** ✅ **RECOMMENDED**

**Migration Steps:**

1. **Week 1:** Foundation (DI, build config, Room setup)
2. **Week 2:** Data layer (repositories, DAOs) - Both systems run in parallel
3. **Week 3:** Domain layer (use cases) - Gradually replace old logic
4. **Week 4:** Presentation layer (ViewModels) - One screen at a time
5. **Week 5:** Background tasks (WorkManager) - Switch over queue processor
6. **Week 6:** Testing & cleanup - Remove old code, finalize tests

---

## Technical Debt Payoff

### Before Refactor:
- ❌ No separation of concerns
- ❌ GlobalScope for coroutines
- ❌ Manual JSON serialization
- ❌ No tests
- ❌ Tight coupling via singletons
- ❌ SharedPreferences blocking main thread
- ❌ No lifecycle awareness
- ❌ Hard to extend or modify

### After Refactor:
- ✅ Clean Architecture (Data/Domain/Presentation)
- ✅ Dependency Injection (Hilt)
- ✅ Room Database with migrations
- ✅ DataStore for preferences
- ✅ WorkManager for background tasks
- ✅ MVVM with StateFlow
- ✅ Comprehensive test coverage
- ✅ Type-safe, reactive, maintainable

---

## Long-Term Roadmap

### Phase 11: Advanced Features (Post-Refactor)
1. **Cloud Sync:** Backup configs to Firebase
2. **Analytics:** Track parse success rates, endpoint latency
3. **Widgets:** Home screen status widget
4. **Advanced Filters:** Regex patterns, time windows
5. **Multi-Sheet Support:** Route different apps to different sheets
6. **Export Logs:** CSV/JSON export functionality
7. **Dark Mode:** Full theme support
8. **Localization:** Multi-language support

### Phase 12: Monitoring & Observability
1. **Firebase Performance:** Track app startup, notification processing time
2. **Custom Metrics:** Parse accuracy, queue depth over time
3. **Health Checks:** Periodic self-tests sent to sheet
4. **User Feedback:** In-app bug reporting

---

## Risk Mitigation

### Key Risks:

1. **Build Issues with Room/KSP**
   - Mitigation: Dedicated testing phase, fallback to manual SQL if needed
   
2. **Breaking Changes During Migration**
   - Mitigation: Feature flags, A/B testing between old/new implementations
   
3. **Data Loss During Migration**
   - Mitigation: Export/import tools, backup mechanisms
   
4. **Performance Regression**
   - Mitigation: Benchmarking before/after, profiling with Android Studio

---

## Success Metrics

### Code Quality:
- Test coverage: >80%
- Cyclomatic complexity: <15 per method
- Max file size: <500 lines
- Build time: <30 seconds (clean build)

### Runtime Performance:
- Notification processing: <100ms
- Database queries: <50ms
- App startup: <2 seconds
- Memory usage: <50MB baseline

### Reliability:
- Crash-free rate: >99.5%
- Parse success rate: >98%
- Queue success rate: >99%
- Battery drain: <2% per hour

---

## Estimated Timeline

**Total Duration:** 5-6 weeks (23 working days)

| Phase | Duration | Deliverable |
|-------|----------|-------------|
| Foundation & Architecture | 3 days | DI, build config, package structure |
| Data Layer Refactor | 2 days | Room, DataStore, repositories |
| Domain Layer & Use Cases | 2 days | Clean business logic, parser interface |
| Presentation Layer | 3 days | ViewModels, reactive UI |
| Background Processing | 2 days | WorkManager integration |
| Error Handling | 2 days | Result types, Timber, monitoring |
| Testing Infrastructure | 3 days | Unit tests, integration tests |
| UI/UX Improvements | 2 days | Material 3, empty states |
| Performance Optimization | 2 days | Indexes, pagination, profiling |
| Documentation & Deployment | 2 days | KDoc, ProGuard, release |

---

## Conclusion

This refactor will transform the AlertsToSheets app from a working prototype into a **production-ready, maintainable, and scalable application**. By following modern Android best practices and clean architecture principles, the codebase will be:

- ✅ **Testable:** 80%+ coverage with unit and integration tests
- ✅ **Maintainable:** Clear separation of concerns, SOLID principles
- ✅ **Scalable:** Easy to add features without breaking existing code
- ✅ **Performant:** Optimized database queries, background processing
- ✅ **Reliable:** Proper error handling, crash reporting, monitoring

**Recommendation:** Begin Phase 1 immediately after getting approval. Use the incremental migration strategy to minimize risk while delivering continuous value.

---

**Questions? Concerns? Let's discuss before starting tomorrow!**

