package com.gaspar.rutassendero.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BeaconScannerManager(private val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Callback when a matching beacon is parsed
    var onBeaconDetected: ((idBeacon: String, latitude: Double, longitude: Double, rssi: Int, txPower: Int, minor: Int) -> Unit)? = null

    companion object {
        private const val TAG = "BeaconScannerManager"
    }

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startScan(): Boolean {
        if (_isScanning.value) return true

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or not supported")
            return false
        }

        // Check system location services (GPS)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationEnabled = locationManager?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } ?: false
        if (!isLocationEnabled) {
            Log.w(TAG, "Location services (GPS) are disabled. BLE scans will not yield results.")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Por favor, activa el GPS / Ubicación del sistema para detectar las balizas de los compañeros.", Toast.LENGTH_LONG).show()
            }
            return false
        }

        // Check runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScanPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasScanPermission || !hasConnectPermission || !hasLocationPermission) {
                Log.w(TAG, "Missing BLUETOOTH_SCAN, BLUETOOTH_CONNECT, or ACCESS_FINE_LOCATION runtime permissions")
                return false
            }
        } else {
            val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocationPermission) {
                Log.w(TAG, "Missing ACCESS_FINE_LOCATION runtime permission")
                return false
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner is null")
            return false
        }

        bluetoothLeScanner = scanner

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If the device supports Extended Advertising, scan for both legacy and extended packets.
            // Some newer devices or custom OS versions advertise iBeacons using extended advertisements
            // which are filtered out if legacy is strictly set to true.
            val supportsExtended = adapter?.isLeExtendedAdvertisingSupported == true
            settingsBuilder.setLegacy(!supportsExtended)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = settingsBuilder.build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result == null) return
                
                val beacon = parseBeaconFromScanRecord(result)?.also { Log.d(TAG, "Beacon parsed via standard manufacturer data (0x004C)") }
                    ?: parseBeacon(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via standard raw bytes AD structures") }
                    ?: parseBeaconFlexible(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via flexible AD structures") }
                    ?: parseBeaconBruteForce(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via brute force raw bytes") }
                if (beacon != null) {
                    onBeaconDetected?.invoke(beacon.idBeacon, beacon.latitude, beacon.longitude, result.rssi, beacon.txPower, beacon.minor)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { result ->
                    val beacon = parseBeaconFromScanRecord(result)?.also { Log.d(TAG, "Beacon parsed via standard manufacturer data (0x004C)") }
                        ?: parseBeacon(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via standard raw bytes AD structures") }
                        ?: parseBeaconFlexible(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via flexible AD structures") }
                        ?: parseBeaconBruteForce(result.scanRecord?.bytes)?.also { Log.d(TAG, "Beacon parsed via brute force raw bytes") }
                    if (beacon != null) {
                        onBeaconDetected?.invoke(beacon.idBeacon, beacon.latitude, beacon.longitude, result.rssi, beacon.txPower, beacon.minor)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            // We use an empty/wildcard ScanFilter. This matches all advertisements, which is required
            // because some Android devices alter the Manufacturer Company ID (e.g., from Apple 0x004C to Samsung 0x0075)
            // when emitting, or fail to match strict hardware manufacturer data filters.
            val filters = listOf(ScanFilter.Builder().build())
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "BLE scan started successfully with wildcard filters")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan: ${e.message}", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}", e)
            return false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return

        val scanner = bluetoothLeScanner
        val callback = scanCallback
        if (scanner != null && callback != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "Missing BLUETOOTH_SCAN to stop scan, attempting anyway...")
                    }
                }
                scanner.stopScan(callback)
                Log.i(TAG, "BLE scan stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan: ${e.message}", e)
            } finally {
                bluetoothLeScanner = null
                scanCallback = null
                _isScanning.value = false
            }
        }
    }

    private fun parseBeaconFromScanRecord(result: ScanResult): BeaconData? {
        val scanRecord = result.scanRecord ?: return null
        val manufacturerData = scanRecord.getManufacturerSpecificData(0x004C) ?: return null
        
        if (manufacturerData.size >= 23 &&
            manufacturerData[0] == 0x02.toByte() &&
            manufacturerData[1] == 0x15.toByte()) {
            
            if (manufacturerData[16] == 0xBE.toByte() && manufacturerData[17] == 0xAC.toByte()) {
                val idBeacon = manufacturerData.sliceArray(2..7).joinToString("") { "%02x".format(it) }
                
                val latBuffer = java.nio.ByteBuffer.wrap(manufacturerData, 8, 4)
                val latitude = latBuffer.getFloat().toDouble()
                
                val lonBuffer = java.nio.ByteBuffer.wrap(manufacturerData, 12, 4)
                val longitude = lonBuffer.getFloat().toDouble()

                val minor = ((manufacturerData[20].toInt() and 0xFF) shl 8) or (manufacturerData[21].toInt() and 0xFF)
                val txPower = manufacturerData[22].toInt()
                return BeaconData(idBeacon, latitude, longitude, txPower, minor)
            }
        }
        return null
    }

    private fun parseBeacon(scanRecordBytes: ByteArray?): BeaconData? {
        if (scanRecordBytes == null) return null
        var i = 0
        while (i < scanRecordBytes.size - 5) {
            val len = scanRecordBytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > scanRecordBytes.size) break

            val type = scanRecordBytes[i + 1].toInt() and 0xFF
            if (type == 0xFF) { // Manufacturer Specific Data
                // iBeacon check: Company ID 0x004C (Apple), patterns: 0x02 0x15
                if (len >= 26 &&
                    scanRecordBytes[i + 2] == 0x4C.toByte() &&
                    scanRecordBytes[i + 3] == 0x00.toByte() &&
                    scanRecordBytes[i + 4] == 0x02.toByte() &&
                    scanRecordBytes[i + 5] == 0x15.toByte()) {

                    val uuidBytes = ByteArray(16)
                    System.arraycopy(scanRecordBytes, i + 6, uuidBytes, 0, 16)

                    // Validate our magic signature in the last 2 bytes of the UUID
                    if (uuidBytes[14] == 0xBE.toByte() && uuidBytes[15] == 0xAC.toByte()) {
                        val idBeacon = uuidBytes.sliceArray(0..5).joinToString("") { "%02x".format(it) }
                        
                        val latBuffer = java.nio.ByteBuffer.wrap(uuidBytes, 6, 4)
                        val latitude = latBuffer.getFloat().toDouble()
                        
                        val lonBuffer = java.nio.ByteBuffer.wrap(uuidBytes, 10, 4)
                        val longitude = lonBuffer.getFloat().toDouble()

                        val minor = ((scanRecordBytes[i + 24].toInt() and 0xFF) shl 8) or (scanRecordBytes[i + 25].toInt() and 0xFF)
                        val txPower = scanRecordBytes[i + 26].toInt() // signed byte
                        return BeaconData(idBeacon, latitude, longitude, txPower, minor)
                    }
                }
            }
            i += 1 + len
        }
        return null
    }

    private fun parseBeaconFlexible(scanRecordBytes: ByteArray?): BeaconData? {
        if (scanRecordBytes == null) return null
        var i = 0
        while (i < scanRecordBytes.size - 5) {
            val len = scanRecordBytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > scanRecordBytes.size) break

            val type = scanRecordBytes[i + 1].toInt() and 0xFF
            if (type == 0xFF) { // Manufacturer Specific Data
                // We ignore the company identifier bytes at i+2 and i+3.
                // Check if the payload starts with iBeacon header 0x02 0x15 at i+4, i+5.
                if (len >= 26 &&
                    scanRecordBytes[i + 4] == 0x02.toByte() &&
                    scanRecordBytes[i + 5] == 0x15.toByte()) {

                    val uuidBytes = ByteArray(16)
                    System.arraycopy(scanRecordBytes, i + 6, uuidBytes, 0, 16)

                    // Validate our magic signature in the last 2 bytes of the UUID
                    if (uuidBytes[14] == 0xBE.toByte() && uuidBytes[15] == 0xAC.toByte()) {
                        val idBeacon = uuidBytes.sliceArray(0..5).joinToString("") { "%02x".format(it) }
                        
                        val latBuffer = java.nio.ByteBuffer.wrap(uuidBytes, 6, 4)
                        val latitude = latBuffer.getFloat().toDouble()
                        
                        val lonBuffer = java.nio.ByteBuffer.wrap(uuidBytes, 10, 4)
                        val longitude = lonBuffer.getFloat().toDouble()

                        val minor = ((scanRecordBytes[i + 24].toInt() and 0xFF) shl 8) or (scanRecordBytes[i + 25].toInt() and 0xFF)
                        val txPower = scanRecordBytes[i + 26].toInt() // signed byte
                        return BeaconData(idBeacon, latitude, longitude, txPower, minor)
                    }
                }
            }
            i += 1 + len
        }
        return null
    }

    private fun parseBeaconBruteForce(bytes: ByteArray?): BeaconData? {
        if (bytes == null) return null
        // Search the entire byte array for 0x02 0x15 followed by 0xBE 0xAC 14 bytes later.
        for (i in 0..(bytes.size - 23)) {
            if (bytes[i] == 0x02.toByte() && bytes[i + 1] == 0x15.toByte()) {
                if (bytes[i + 16] == 0xBE.toByte() && bytes[i + 17] == 0xAC.toByte()) {
                    val idBeacon = bytes.sliceArray(i + 2..i + 7).joinToString("") { "%02x".format(it) }
                    
                    val latBuffer = java.nio.ByteBuffer.wrap(bytes, i + 8, 4)
                    val latitude = latBuffer.getFloat().toDouble()
                    
                    val lonBuffer = java.nio.ByteBuffer.wrap(bytes, i + 12, 4)
                    val longitude = lonBuffer.getFloat().toDouble()

                    val minor = ((bytes[i + 20].toInt() and 0xFF) shl 8) or (bytes[i + 21].toInt() and 0xFF)
                    val txPower = bytes[i + 22].toInt()
                    return BeaconData(idBeacon, latitude, longitude, txPower, minor)
                }
            }
        }
        return null
    }

    private data class BeaconData(
        val idBeacon: String,
        val latitude: Double,
        val longitude: Double,
        val txPower: Int,
        val minor: Int
    )
}
