package com.polentita.music.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import com.polentita.music.R
import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.core.storage.PreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class NetworkBlockReason(@StringRes val messageRes: Int) {
    OFFLINE_MODE(R.string.network_block_offline),
    NO_CONNECTION(R.string.network_block_no_connection),
    WIFI_REQUIRED(R.string.network_block_wifi_required),
}

data class ConnectivityState(
    val connected: Boolean = false,
    val wifi: Boolean = false,
    val metered: Boolean = true,
)

data class NetworkAccessState(
    val offlineMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val connected: Boolean = false,
    val wifi: Boolean = false,
    val metered: Boolean = true,
    val remoteSearchAllowed: Boolean = false,
    val previewAllowed: Boolean = false,
    val downloadAllowed: Boolean = false,
    val remoteBlockReason: NetworkBlockReason? = NetworkBlockReason.NO_CONNECTION,
    val downloadBlockReason: NetworkBlockReason? = NetworkBlockReason.NO_CONNECTION,
)

fun evaluateNetworkAccess(
    preferences: AppPreferences,
    connectivity: ConnectivityState,
): NetworkAccessState {
    val remoteReason = when {
        preferences.offlineMode -> NetworkBlockReason.OFFLINE_MODE
        !connectivity.connected -> NetworkBlockReason.NO_CONNECTION
        else -> null
    }
    val downloadReason = when {
        remoteReason != null -> remoteReason
        preferences.wifiOnlyDownloads && (!connectivity.wifi || connectivity.metered) -> {
            NetworkBlockReason.WIFI_REQUIRED
        }
        else -> null
    }
    return NetworkAccessState(
        offlineMode = preferences.offlineMode,
        wifiOnlyDownloads = preferences.wifiOnlyDownloads,
        connected = connectivity.connected,
        wifi = connectivity.wifi,
        metered = connectivity.metered,
        remoteSearchAllowed = remoteReason == null,
        previewAllowed = remoteReason == null,
        downloadAllowed = downloadReason == null,
        remoteBlockReason = remoteReason,
        downloadBlockReason = downloadReason,
    )
}

interface NetworkAccessPolicy {
    val state: StateFlow<NetworkAccessState>
    suspend fun current(): NetworkAccessState
}

object UnrestrictedNetworkAccessPolicy : NetworkAccessPolicy {
    private val allowed = NetworkAccessState(
        connected = true,
        wifi = true,
        metered = false,
        remoteSearchAllowed = true,
        previewAllowed = true,
        downloadAllowed = true,
        remoteBlockReason = null,
        downloadBlockReason = null,
    )
    private val mutableState = MutableStateFlow(allowed)
    override val state: StateFlow<NetworkAccessState> = mutableState.asStateFlow()
    override suspend fun current(): NetworkAccessState = allowed
}

class NetworkAccessBlockedException(
    val reason: NetworkBlockReason,
) : IllegalStateException(reason.userMessage())

fun NetworkBlockReason.userMessage(): String = when (this) {
    NetworkBlockReason.OFFLINE_MODE -> "Desactiva el modo sin conexión para continuar"
    NetworkBlockReason.NO_CONNECTION -> "Sin conexión a Internet"
    NetworkBlockReason.WIFI_REQUIRED -> "Conéctate a una red Wi-Fi para descargar"
}

@Singleton
class AndroidNetworkAccessPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: PreferencesStore,
) : NetworkAccessPolicy {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectivity = MutableStateFlow(readConnectivity())
    private val mutableState = MutableStateFlow(
        NetworkAccessState(
            connected = connectivity.value.connected,
            wifi = connectivity.value.wifi,
            metered = connectivity.value.metered,
        ),
    )
    override val state: StateFlow<NetworkAccessState> = mutableState.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()
        override fun onLost(network: Network) = refreshConnectivity()
        override fun onUnavailable() = refreshConnectivity()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshConnectivity()
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
        scope.launch {
            combine(preferencesStore.preferences, connectivity, ::evaluateNetworkAccess)
                .collect { mutableState.value = it }
        }
    }

    override suspend fun current(): NetworkAccessState = evaluateNetworkAccess(
        preferencesStore.current(),
        readConnectivity(),
    )

    private fun refreshConnectivity() {
        connectivity.value = readConnectivity()
    }

    private fun readConnectivity(): ConnectivityState {
        val network = connectivityManager.activeNetwork ?: return ConnectivityState()
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectivityState()
        val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return ConnectivityState(
            connected = connected,
            wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            metered = connectivityManager.isActiveNetworkMetered ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }
}
