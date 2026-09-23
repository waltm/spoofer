package com.spoofer.di

import android.content.Context
import android.location.LocationManager
import com.spoofer.location.JsonPatchedClient
import com.spoofer.location.MockLocationProvider
import com.spoofer.location.mode.LocationMode
import com.spoofer.location.mode.LocationModeSwitcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideModeSwitcher(
        @ApplicationContext context: Context,
        mockLocationProvider: MockLocationProvider,
        jsonPatchedClient: JsonPatchedClient,
    ): LocationModeSwitcher {
        return LocationModeSwitcher(context, mockLocationProvider, jsonPatchedClient)
    }

    @Provides
    @Singleton
    fun provideMockLocationProvider(
        locationManager: LocationManager,
    ): MockLocationProvider {
        return MockLocationProvider(locationManager)
    }

    @Provides
    @Singleton
    fun provideJsonPatchedClient(): JsonPatchedClient {
        return JsonPatchedClient()
    }

    @Provides
    @Singleton
    fun provideDefaultMode(): LocationMode {
        return LocationMode.DebugMode()
    }
}
