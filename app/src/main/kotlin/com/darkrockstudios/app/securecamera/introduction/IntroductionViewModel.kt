package com.darkrockstudios.app.securecamera.introduction

import com.darkrockstudios.app.securecamera.security.SecurityLevel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IntroductionViewModel {
    val uiState: StateFlow<IntroductionUiState>
    val skipToPage: SharedFlow<Int>

    fun setPage(page: Int)
    suspend fun navigateToNextPage()
    suspend fun navigateToSecurity()

    fun createPin(pin: String, confirmPin: String)
    fun toggleBiometricsRequired()
    fun toggleEphemeralKey()
}

data class IntroductionUiState(
    val slides: List<IntroductionSlide> = emptyList(),
    val errorMessage: String? = null,
    val pinCreated: Boolean = false,
    val securityLevel: SecurityLevel,
    val requireBiometrics: Boolean = false,
    val ephemeralKey: Boolean = false,
    val currentPage: Int = 0,
    val isCreatingPin: Boolean = false,
    val pinSize: IntRange,
)