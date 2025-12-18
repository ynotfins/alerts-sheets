# Refactor Day 1: Foundation Setup Checklist

**Goal:** Set up the foundation for Clean Architecture with Hilt DI and modern build system.

**Estimated Duration:** 6-8 hours  
**Risk Level:** 🟢 Low (infrastructure only, no logic changes)

---

## Pre-Flight Checklist

### ✅ Backup & Branch
- [ ] Create backup branch: `git checkout -b backup/pre-refactor`
- [ ] Push backup: `git push origin backup/pre-refactor`
- [ ] Create refactor branch: `git checkout -b refactor/clean-architecture`
- [ ] Verify clean working directory: `git status`

### ✅ Environment Verification
- [ ] Android Studio: Hedgehog (2023.1.1) or newer
- [ ] JDK 17 installed and configured
- [ ] Gradle: 8.2+ (check `gradle/wrapper/gradle-wrapper.properties`)
- [ ] Device/Emulator: API 26+ available for testing

---

## Phase 1A: Build System Upgrade (2 hours)

### Step 1: Update Gradle Plugins
**File:** `android/build.gradle` (project-level)

```gradle
buildscript {
    ext.kotlin_version = '1.9.20'
    ext.hilt_version = '2.50'
    
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.2'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "com.google.dagger:hilt-android-gradle-plugin:$hilt_version"
    }
}
```

**File:** `android/app/build.gradle`

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.devtools.ksp' version '1.9.20-1.0.14'
    id 'kotlin-parcelize'
    id 'com.google.dagger.hilt.android'
}

android {
    // ... existing config ...
    
    buildFeatures {
        viewBinding true
        buildConfig true
    }
}

dependencies {
    // Keep existing dependencies
    
    // ADD NEW:
    // Hilt DI
    implementation "com.google.dagger:hilt-android:2.50"
    ksp "com.google.dagger:hilt-compiler:2.50"
    
    // Room (with KSP)
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    ksp 'androidx.room:room-compiler:2.6.1'
    
    // DataStore
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
    
    // Timber
    implementation 'com.jakewharton.timber:timber:5.0.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito.kotlin:mockito-kotlin:5.2.1'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
    testImplementation 'androidx.arch.core:core-testing:2.2.0'
}
```

**Verify:**
```powershell
cd android
.\gradlew.bat clean build --no-daemon
# Expected: BUILD SUCCESSFUL
```

### Step 2: Create Application Class
**New File:** `app/src/main/java/com/example/alertsheets/AlertsApp.kt`

```kotlin
package com.example.alertsheets

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AlertsApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("AlertsApp initialized")
    }
}
```

**Update:** `AndroidManifest.xml`

```xml
<application
    android:name=".AlertsApp"
    ...>
```

**Verify:**
```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
adb install app/build/outputs/apk/debug/app-debug.apk
adb logcat -s AlertsApp:I
# Expected: "AlertsApp initialized"
```

---

## Phase 1B: Package Structure (1 hour)

### Step 3: Create New Package Structure
**Run in terminal:**

```powershell
cd app\src\main\java\com\example\alertsheets

# Create directories
mkdir app, app\di
mkdir data, data\local, data\local\db, data\local\db\dao, data\local\db\entities, data\local\preferences
mkdir data\remote, data\remote\api, data\remote\dto
mkdir data\repository
mkdir domain, domain\model, domain\usecase, domain\parser
mkdir presentation, presentation\ui, presentation\service
mkdir worker
mkdir util
```

**Expected Structure:**
```
com.example.alertsheets/
├── app/
│   └── di/
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── dao/
│   │   │   └── entities/
│   │   └── preferences/
│   ├── remote/
│   │   ├── api/
│   │   └── dto/
│   └── repository/
├── domain/
│   ├── model/
│   ├── usecase/
│   └── parser/
├── presentation/
│   ├── ui/
│   └── service/
├── worker/
└── util/
```

---

## Phase 1C: Dependency Injection Setup (2 hours)

### Step 4: Create DI Modules
**New File:** `app/di/AppModule.kt`

```kotlin
package com.example.alertsheets.app.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
}
```

**New File:** `app/di/DatabaseModule.kt`

```kotlin
package com.example.alertsheets.app.di

import android.content.Context
import androidx.room.Room
import com.example.alertsheets.data.local.db.AlertsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAlertsDatabase(
        @ApplicationContext context: Context
    ): AlertsDatabase {
        return Room.databaseBuilder(
            context,
            AlertsDatabase::class.java,
            "alerts_database"
        )
            .fallbackToDestructiveMigration() // TODO: Add proper migrations later
            .build()
    }
    
    // DAOs will be added here incrementally
}
```

**New File:** `app/di/NetworkModule.kt`

```kotlin
package com.example.alertsheets.app.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
```

**Verify:**
```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
# Expected: BUILD SUCCESSFUL (Hilt generates code)
```

### Step 5: Create Room Database Stub
**New File:** `data/local/db/AlertsDatabase.kt`

```kotlin
package com.example.alertsheets.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.alertsheets.data.local.db.entities.LogEntryEntity

@Database(
    entities = [
        LogEntryEntity::class
        // More entities will be added incrementally
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AlertsDatabase : RoomDatabase() {
    // DAOs will be added here
    // abstract fun logEntryDao(): LogEntryDao
}
```

**New File:** `data/local/db/Converters.kt`

```kotlin
package com.example.alertsheets.data.local.db

import androidx.room.TypeConverter
import com.example.alertsheets.LogStatus
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
    
    @TypeConverter
    fun fromLogStatus(value: LogStatus): String = value.name
    
    @TypeConverter
    fun toLogStatus(value: String): LogStatus = LogStatus.valueOf(value)
}
```

**New File:** `data/local/db/entities/LogEntryEntity.kt`

```kotlin
package com.example.alertsheets.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.alertsheets.LogStatus

@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    val title: String,
    val content: String,
    val status: LogStatus,
    @ColumnInfo(name = "raw_json") val rawJson: String
)
```

**Verify:**
```powershell
.\gradlew.bat :app:kspDebugKotlin --no-daemon
# Expected: Room generates DAO implementations
```

---

## Phase 1D: Utility Setup (1 hour)

### Step 6: Create Utility Classes
**New File:** `util/Result.kt`

```kotlin
package com.example.alertsheets.util

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

**New File:** `util/Constants.kt`

```kotlin
package com.example.alertsheets.util

object Constants {
    const val BNN_PACKAGE_NAME = "us.bnn.newsapp"
    const val BNN_MARKER = "<C> BNN"
    const val MIN_PIPE_COUNT_FOR_BNN = 4
    const val MAX_LOG_ENTRIES = 200
    const val MAX_QUEUE_RETRIES = 10
    const val DEBOUNCE_TIME_MS = 2000L
}
```

**New File:** `util/Extensions.kt`

```kotlin
package com.example.alertsheets.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(@StringRes messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, messageResId, duration).show()
}
```

---

## Phase 1E: Replace Log with Timber (30 mins)

### Step 7: Global Find & Replace
**In Android Studio:**

1. Press `Ctrl+Shift+R` (Replace in Path)
2. Find: `Log\.d\(`
   Replace: `Timber.d(`
3. Find: `Log\.i\(`
   Replace: `Timber.i(`
4. Find: `Log\.w\(`
   Replace: `Timber.w(`
5. Find: `Log\.e\(`
   Replace: `Timber.e(`

**Add Imports:**
```kotlin
// Remove: import android.util.Log
// Add: import timber.log.Timber
```

**Files to Update:**
- NotificationService.kt
- Parser.kt
- QueueProcessor.kt
- NetworkClient.kt
- LogRepository.kt
- All Activities

**Verify:**
```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
adb logcat -s AlertsApp:D Timber:D
# Logs should now use Timber tags
```

---

## Day 1 Validation Checklist

### Build Success
- [ ] `.\gradlew.bat clean` succeeds
- [ ] `.\gradlew.bat :app:assembleDebug` succeeds
- [ ] No compilation errors
- [ ] Hilt code generation completes

### Runtime Verification
- [ ] App installs on device/emulator
- [ ] App launches without crash
- [ ] AlertsApp.onCreate() logs appear
- [ ] Timber logs visible in logcat
- [ ] Existing functionality still works (notifications intercepted)

### Code Quality
- [ ] No new lint errors introduced
- [ ] No deprecated API warnings (except existing ones)
- [ ] BuildConfig.DEBUG accessible

### Git Checkpoint
```powershell
git add .
git commit -m "feat: Day 1 - Foundation setup (Hilt DI, Room stub, Timber)"
git push origin refactor/clean-architecture
```

---

## Troubleshooting

### Issue: KSP Build Fails
**Error:** `KSP annotation processor not found`

**Fix:**
```gradle
// In settings.gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### Issue: Hilt Dependency Injection Fails
**Error:** `@HiltAndroidApp annotated class not found`

**Fix:**
1. Clean build: `.\gradlew.bat clean`
2. Invalidate caches: Android Studio → File → Invalidate Caches
3. Rebuild: `.\gradlew.bat :app:assembleDebug`

### Issue: Room Schema Export Error
**Error:** `Cannot export schema`

**Fix:**
```gradle
android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += ["room.schemaLocation": "$projectDir/schemas".toString()]
            }
        }
    }
}
```

---

## End of Day 1

**Expected Outcome:**
✅ Hilt DI configured and operational  
✅ Room database stub created  
✅ Package structure for Clean Architecture in place  
✅ Timber replacing Log.*  
✅ Build succeeds with KSP  
✅ App runs with existing functionality intact  

**Next Steps (Day 2):**
- Migrate LogRepository to Room
- Create LogEntryDao with queries
- Implement LogRepositoryImpl with DI
- Write first unit tests for repository

**Time for celebration!** 🎉 Foundation is solid, now we build.

---

**Questions? Issues? Document them here for tomorrow's standup.**

