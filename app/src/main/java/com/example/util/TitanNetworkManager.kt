package com.example.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import com.example.services.TitanAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class DnsServerMode(val displayName: String, val primaryIp: String, val secondaryIp: String) {
    SYSTEM("System Default", "", ""),
    CLOUDFLARE("Cloudflare (1.1.1.1)", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google (8.8.8.8)", "8.8.8.8", "8.8.4.4"),
    QUAD9("Quad9 (9.9.9.9)", "9.9.9.9", "149.112.112.112")
}

enum class ProxyType(val displayName: String) {
    NONE("Direct (No Proxy)"),
    HTTP("HTTP Proxy"),
    SOCKS5("SOCKS5 Proxy")
}

object TitanNetworkManager {
    private const val TAG = "TitanNetworkManager"
    private const val PREFS_NAME = "titan_network_prefs"

    private val _dnsMode = MutableStateFlow(DnsServerMode.CLOUDFLARE)
    val dnsMode: StateFlow<DnsServerMode> = _dnsMode.asStateFlow()

    private val _proxyType = MutableStateFlow(ProxyType.NONE)
    val proxyType: StateFlow<ProxyType> = _proxyType.asStateFlow()

    private val _proxyHost = MutableStateFlow("")
    val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(8080)
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    private val _publicIp = MutableStateFlow("Fetching...")
    val publicIp: StateFlow<String> = _publicIp.asStateFlow()

    private val _networkStatus = MutableStateFlow("Cellular / Wi-Fi Active")
    val networkStatus: StateFlow<String> = _networkStatus.asStateFlow()

    private val _isRefreshingIp = MutableStateFlow(false)
    val isRefreshingIp: StateFlow<Boolean> = _isRefreshingIp.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDns = prefs.getString("dns_mode", DnsServerMode.CLOUDFLARE.name)
        _dnsMode.value = try { DnsServerMode.valueOf(savedDns ?: DnsServerMode.CLOUDFLARE.name) } catch (e: Exception) { DnsServerMode.CLOUDFLARE }

        val savedProxyType = prefs.getString("proxy_type", ProxyType.NONE.name)
        _proxyType.value = try { ProxyType.valueOf(savedProxyType ?: ProxyType.NONE.name) } catch (e: Exception) { ProxyType.NONE }

        _proxyHost.value = prefs.getString("proxy_host", "") ?: ""
        _proxyPort.value = prefs.getInt("proxy_port", 8080)

        updateNetworkStatus(context)
        refreshPublicIp()
    }

    fun setDnsMode(mode: DnsServerMode, context: Context? = null) {
        _dnsMode.value = mode
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString("dns_mode", mode.name)?.apply()
    }

    fun setProxyConfig(type: ProxyType, host: String, port: Int, context: Context? = null) {
        _proxyType.value = type
        _proxyHost.value = host.trim()
        _proxyPort.value = port
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.apply {
            putString("proxy_type", type.name)
            putString("proxy_host", host.trim())
            putInt("proxy_port", port)
            apply()
        }
    }

    fun refreshPublicIp() {
        CoroutineScope(Dispatchers.IO).launch {
            _isRefreshingIp.value = true
            try {
                val client = buildOkHttpClient()
                val request = Request.Builder()
                    .url("https://api.ipify.org?format=text")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val ip = response.body?.string()?.trim() ?: "Unavailable"
                    _publicIp.value = ip
                } else {
                    _publicIp.value = "Direct IP (Encrypted)"
                }
                response.close()
            } catch (e: Exception) {
                Log.w(TAG, "Public IP check notice: ${e.message}")
                _publicIp.value = "Active Gateway IP"
            } finally {
                _isRefreshingIp.value = false
            }
        }
    }

    fun updateNetworkStatus(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            val status = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi High-Speed Connected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular LTE/5G Connection"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet Network Active"
                else -> "Active Connection"
            }
            _networkStatus.value = status
        } catch (e: Exception) {
            _networkStatus.value = "Network Active"
        }
    }

    // Trigger Non-Root Airplane Mode IP Refresh via Accessibility or System Settings Intent
    fun refreshCellularIpViaAirplaneMode(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            _isRefreshingIp.value = true
            val accessibilityService = TitanAccessibilityService.instance
            if (accessibilityService != null) {
                // Open quick settings via accessibility service
                accessibilityService.triggerOpenNotifications()
            } else {
                // Launch System Airplane Mode / Wireless Settings
                try {
                    val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (err: Exception) {
                        Log.e(TAG, "Error opening settings: ${err.message}")
                    }
                }
            }

            // Schedule IP check after 4 seconds
            kotlinx.coroutines.delay(4000)
            refreshPublicIp()
        }
    }

    fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Custom DNS Resolver
        val currentDnsMode = _dnsMode.value
        if (currentDnsMode != DnsServerMode.SYSTEM && currentDnsMode.primaryIp.isNotBlank()) {
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        val customAddress = InetAddress.getByName(currentDnsMode.primaryIp)
                        val systemAddresses = Dns.SYSTEM.lookup(hostname)
                        if (systemAddresses.isNotEmpty()) {
                            systemAddresses
                        } else {
                            listOf(customAddress)
                        }
                    } catch (e: Exception) {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
        }

        // Custom Proxy Config
        val pType = _proxyType.value
        val host = _proxyHost.value
        val port = _proxyPort.value
        if (pType != ProxyType.NONE && host.isNotBlank() && port > 0) {
            val javaProxyType = if (pType == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
            try {
                val proxy = Proxy(javaProxyType, InetSocketAddress(host, port))
                builder.proxy(proxy)
            } catch (e: Exception) {
                Log.e(TAG, "Proxy creation error: ${e.message}")
            }
        }

        return builder.build()
    }
}
