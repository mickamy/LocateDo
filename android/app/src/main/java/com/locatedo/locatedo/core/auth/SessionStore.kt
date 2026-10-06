package com.locatedo.locatedo.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.locatedo.locatedo.core.common.di.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

// Its own file, excluded from Auto Backup so a session never follows the data onto another device.
class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {
    private object Keys {
        val userId = stringPreferencesKey("userId")
        val accessToken = stringPreferencesKey("accessToken")
        val accessTokenExpiresAt = longPreferencesKey("accessTokenExpiresAt")
        val refreshToken = stringPreferencesKey("refreshToken")
    }

    override suspend fun load(): Session? {
        val stored = dataStore.data.first()
        val userId = stored[Keys.userId]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val accessToken = stored[Keys.accessToken] ?: return null
        val expiresAt = stored[Keys.accessTokenExpiresAt] ?: return null
        val refreshToken = stored[Keys.refreshToken] ?: return null
        return Session(userId, accessToken, Instant.ofEpochMilli(expiresAt), refreshToken)
    }

    override suspend fun save(session: Session) {
        dataStore.edit {
            it[Keys.userId] = session.userId.toString()
            it[Keys.accessToken] = session.accessToken
            it[Keys.accessTokenExpiresAt] = session.accessTokenExpiresAt.toEpochMilli()
            it[Keys.refreshToken] = session.refreshToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    fun sessionStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): SessionStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO)) {
            context.preferencesDataStoreFile("session")
        }
        return DataStoreSessionStore(dataStore)
    }
}
