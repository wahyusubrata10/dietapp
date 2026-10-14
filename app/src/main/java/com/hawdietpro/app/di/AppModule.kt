package com.hawdietpro.app.di

import android.content.Context
import androidx.room.Room
import com.hawdietpro.app.data.local.HawDatabase
import com.hawdietpro.app.data.local.Migrations
import com.hawdietpro.app.data.local.PreMigrationBackup
import com.hawdietpro.app.data.local.dao.FoodReferenceDao
import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.local.dao.UserProfileDao
import com.hawdietpro.app.data.local.dao.WeightLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HawDatabase {
        PreMigrationBackup.runIfNeeded(context)
        return Room.databaseBuilder(context, HawDatabase::class.java, HawDatabase.DATABASE_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
    }

    @Provides fun provideMealDao(db: HawDatabase): MealDao = db.mealDao()
    @Provides fun provideFoodReferenceDao(db: HawDatabase): FoodReferenceDao = db.foodReferenceDao()
    @Provides fun provideWeightLogDao(db: HawDatabase): WeightLogDao = db.weightLogDao()
    @Provides fun provideUserProfileDao(db: HawDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun providePendingAnalysisDao(db: HawDatabase): PendingAnalysisDao = db.pendingAnalysisDao()
}
