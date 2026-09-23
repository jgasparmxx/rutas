package com.gaspar.rutassendero.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            val hasTransport = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            val hasInternet = activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            return hasTransport && hasInternet && isValidated
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Comprueba mediante socket si realmente hay salida al exterior (útil para routers Wi-Fi sin salida a internet).
     */
    suspend fun hasRealInternetConnection(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val socket = java.net.Socket()
            val sockaddr = java.net.InetSocketAddress("8.8.8.8", 53)
            socket.connect(sockaddr, 1500)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
