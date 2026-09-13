package com.quransunah.app.hydration

import com.quransunah.app.core.AppConstants
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.fonts.QcfFontManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface HydrationState {
    data object Pending : HydrationState
    data object Ready : HydrationState
    data class Failed(val message: String) : HydrationState
}

@Singleton
class HydrationGate @Inject constructor(
    private val preferences: UserPreferences,
    private val mushafRepository: MushafRepository,
    private val fontManager: QcfFontManager,
) {
    private val _state = MutableStateFlow<HydrationState>(HydrationState.Pending)
    val state: StateFlow<HydrationState> = _state

    val keepSplash: Boolean
        get() = _state.value is HydrationState.Pending

    suspend fun hydrate() {
        _state.value = HydrationState.Pending
        val result = runCatching {
            withTimeoutOrNull(AppConstants.HYDRATION_TIMEOUT_MS) {
                coroutineScope {
                    preferences.settings.first()
                    mushafRepository.warmup().getOrThrow()
                    runCatching { fontManager.ensureMaps() }
                    true
                }
            }
        }
        _state.value = result.fold(
            onSuccess = { opened ->
                if (opened == null) HydrationState.Failed("انتهت مهلة تجهيز المصحف")
                else HydrationState.Ready
            },
            onFailure = { HydrationState.Failed(it.message ?: "تعذّر تجهيز المصحف") },
        )
    }

    suspend fun retry() {
        hydrate()
    }
}
