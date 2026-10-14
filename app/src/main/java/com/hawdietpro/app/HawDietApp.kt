package com.hawdietpro.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hawdietpro.app.core.Notifications
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.FoodRepository
import com.hawdietpro.app.di.ApplicationScope
import com.hawdietpro.app.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HawDietApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var foods: FoodRepository
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var remoteConfig: RemoteConfigStore

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            runCatching { foods.seedIfNeeded() }
        }
        scheduler.schedulePeriodic(remoteConfig.appConfig.value.updateCheckIntervalHours)
        scheduler.enqueuePendingAnalysis()
    }
}
