package com.gaspar.rutassendero.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

enum class BleSupportStatus {
    SUPPORTED,
    NOT_SUPPORTED,
    BLUETOOTH_DISABLED,
    ADVERTISING_NOT_SUPPORTED
}

/**
 * Manager responsible for BLE (Bluetooth Low Energy) advertising.
 * It encodes application-specific data (like GPS coordinates) into an iBeacon standard format.
 * 
 * TODO (Best Practice): Consider migrating `startEmitting` from using callbacks (`onSuccess`/`onFailure`) 
 * to Kotlin Coroutines using `suspendCancellableCoroutine` for a more idiomatic and readable flow,
 * avoiding potential "callback hell".
 */
class BeaconEmitterManager(private val context: Context) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    companion object {
        private const val TAG = "BeaconEmitterManager"
        const val NAMESPACE_ID = "8c3a9f21b76e4d05a912" // 10 bytes (20 hex chars)
        const val DEFAULT_MAJOR = 1
        const val DEFAULT_MINOR = 1
        const val DEFAULT_TX_POWER = -59 // Measured power at 1 meter in dBm
    }

    /**
     * Checks if the device supports BLE and BLE advertising, and whether Bluetooth is enabled.
     */
    fun checkBleHardwareSupport(): BleSupportStatus {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return BleSupportStatus.NOT_SUPPORTED

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BleSupportStatus.NOT_SUPPORTED
        }

        if (!adapter.isEnabled) {
            return BleSupportStatus.BLUETOOTH_DISABLED
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            return BleSupportStatus.ADVERTISING_NOT_SUPPORTED
        }

        return BleSupportStatus.SUPPORTED
    }

    /**
     * Starts emitting as an iBeacon encoding the idBeacon + GPS latitude & longitude
     * into the 16-byte Proximity UUID.
     * 
     * @param idBeacon The unique identifier for the beacon.
     * @param latitude Current latitude (optional).
     * @param longitude Current longitude (optional).
     * @param onSuccess Callback triggered when advertising successfully starts.
     * @param onFailure Callback triggered on failure, passing an error code.
     */
    fun startEmitting(
        idBeacon: String,
        latitude: Float? = null,
        longitude: Float? = null,
        major: Int = DEFAULT_MAJOR,
        minor: Int = DEFAULT_MINOR,
        txPower: Int = DEFAULT_TX_POWER,
        onSuccess: () -> Unit,
        onFailure: (Int) -> Unit
    ) {
        val support = checkBleHardwareSupport()
        if (support != BleSupportStatus.SUPPORTED) {
            Log.w(TAG, "Hardware does not support BLE advertising or Bluetooth is disabled: $support")
            onFailure(-1)
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val leAdvertiser = adapter.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BluetoothLeAdvertiser is null")
            onFailure(-2)
            return
        }

        // Check runtime permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing BLUETOOTH_ADVERTISE runtime permission")
            onFailure(-3)
            return
        }

        // Stop any active emission first
        stopEmitting()

        advertiser = leAdvertiser

        // 1. Configure settings
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // Must be true so the OS includes the mandatory Flags AD structure (02 01 06). Many Android chipsets (like Xiaomi/Redmi) drop packets that lack this structure.
            .build()

        // 2. Build iBeacon standard manufacturer specific data
        val uuidBytes = ByteArray(16)
        try {
            val idBeaconBytes = hexStringToByteArray(idBeacon)
            System.arraycopy(idBeaconBytes, 0, uuidBytes, 0, idBeaconBytes.size.coerceAtMost(6))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing idBeacon hex string: ${e.message}", e)
        }

        val latBits = java.lang.Float.floatToIntBits(latitude ?: 0.0f)
        uuidBytes[6] = ((latBits shr 24) and 0xFF).toByte()
        uuidBytes[7] = ((latBits shr 16) and 0xFF).toByte()
        uuidBytes[8] = ((latBits shr 8) and 0xFF).toByte()
        uuidBytes[9] = (latBits and 0xFF).toByte()

        val lonBits = java.lang.Float.floatToIntBits(longitude ?: 0.0f)
        uuidBytes[10] = ((lonBits shr 24) and 0xFF).toByte()
        uuidBytes[11] = ((lonBits shr 16) and 0xFF).toByte()
        uuidBytes[12] = ((lonBits shr 8) and 0xFF).toByte()
        uuidBytes[13] = (lonBits and 0xFF).toByte()

        uuidBytes[14] = 0xBE.toByte()
        uuidBytes[15] = 0xAC.toByte()

        val beaconData = ByteArray(23)
        beaconData[0] = 0x02 // Type indicator (iBeacon)
        beaconData[1] = 0x15 // Remaining payload length (21 bytes)
        
        // Copy 16-byte UUID
        System.arraycopy(uuidBytes, 0, beaconData, 2, 16)

        // Major (2 bytes)
        beaconData[18] = ((major shr 8) and 0xFF).toByte()
        beaconData[19] = (major and 0xFF).toByte()

        // Minor (2 bytes)
        beaconData[20] = ((minor shr 8) and 0xFF).toByte()
        beaconData[21] = (minor and 0xFF).toByte()

        // Tx Power (1 byte)
        beaconData[22] = txPower.toByte()

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, beaconData) // Apple company identifier
            .build()

        // 3. Define callback
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.i(TAG, "BLE iBeacon advertising started successfully")
                onSuccess()
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "BLE iBeacon advertising failed to start. Error code: $errorCode")
                onFailure(errorCode)
            }
        }
        advertiseCallback = callback

        // 4. Start advertising
        try {
            leAdvertiser.startAdvertising(settings, advertiseData, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting advertising: ${e.message}", e)
            onFailure(-3)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}", e)
            onFailure(-4)
        }
    }

    /**
     * Stops the active BLE advertising.
     */
    fun stopEmitting() {
        if (advertiser != null && advertiseCallback != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing BLUETOOTH_ADVERTISE to stop advertising, attempting anyway...")
                }
                advertiser?.stopAdvertising(advertiseCallback)
                Log.i(TAG, "BLE iBeacon advertising stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE advertising: ${e.message}", e)
            } finally {
                advertiser = null
                advertiseCallback = null
            }
        }
    }

    /**
     * Utility method to convert a hex string to a byte array.
     */
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            val hDigit = Character.digit(s[i], 16)
            val lDigit = Character.digit(s[i + 1], 16)
            if (hDigit == -1 || lDigit == -1) {
                throw IllegalArgumentException("Invalid hex character in string: $s")
            }
            data[i / 2] = ((hDigit shl 4) + lDigit).toByte()
        }
        return data
    }
}
