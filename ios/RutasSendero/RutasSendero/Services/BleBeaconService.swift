//
//  BleBeaconService.swift
//  RutasSendero
//
//  Servicio de hardware Bluetooth Low Energy (BLE) para senderismo.
//  Implementa BleBeaconBroadcasterProtocol y BleBeaconScannerProtocol
//  según BLE_HARDWARE_PROTOCOL.md.
//

import Foundation
import CoreBluetooth
import CoreLocation
import Observation

// MARK: - Decodificador y Estructura de Datos de Baliza

public struct BeaconPayload: Sendable, Equatable {
    public let idBeacon: String
    public let latitude: Double
    public let longitude: Double
    public let major: Int
    public let minor: Int
    public let txPower: Int
    public let isEmergency: Bool
    
    public init(idBeacon: String, latitude: Double, longitude: Double, major: Int = 1, minor: Int = 1, txPower: Int = -59, isEmergency: Bool = false) {
        self.idBeacon = idBeacon
        self.latitude = latitude
        self.longitude = longitude
        self.major = major
        self.minor = minor
        self.txPower = txPower
        self.isEmergency = isEmergency
    }

    /// Parsea los bytes de Manufacturer Data recibidos por CoreBluetooth
    public static func parse(manufacturerData: Data) -> BeaconPayload? {
        // Formato Apple iBeacon esperado:
        // [0..1] Company ID 0x004C (en little endian: 0x4C, 0x00)
        // [2] Type 0x02 (iBeacon)
        // [3] Length 0x15 (21 bytes)
        // [4..19] Proximity UUID (16 bytes custom)
        // [20..21] Major (2 bytes)
        // [22..23] Minor (2 bytes)
        // [24] TxPower (1 byte)
        
        var offset = 0
        if manufacturerData.count >= 25 && manufacturerData[0] == 0x4C && manufacturerData[1] == 0x00 {
            offset = 2
        } else if manufacturerData.count < 23 {
            return nil
        }
        
        guard manufacturerData[offset] == 0x02 && manufacturerData[offset + 1] == 0x15 else {
            return nil
        }
        
        // Offset de los 16 bytes de UUID
        let uuidOffset = offset + 2
        guard manufacturerData.count >= uuidOffset + 16 + 5 else { return nil }
        
        // Validar firma mágica 0xBE 0xAC en los bytes 14 y 15 del UUID
        let magicByte1 = manufacturerData[uuidOffset + 14]
        let magicByte2 = manufacturerData[uuidOffset + 15]
        guard magicByte1 == 0xBE && magicByte2 == 0xAC else { return nil }

        // 1. idBeacon (6 bytes = 12 caracteres hex)
        let idBytes = manufacturerData.subdata(in: uuidOffset..<(uuidOffset + 6))
        let idBeacon = idBytes.map { String(format: "%02x", $0) }.joined()

        // 2. Latitud (Float32 Big-Endian)
        let latBits = manufacturerData.subdata(in: (uuidOffset + 6)..<(uuidOffset + 10))
            .withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
        let latitude = Double(Float(bitPattern: latBits))

        // 3. Longitud (Float32 Big-Endian)
        let lonBits = manufacturerData.subdata(in: (uuidOffset + 10)..<(uuidOffset + 14))
            .withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
        let longitude = Double(Float(bitPattern: lonBits))

        // 4. Major y Minor
        let majorOffset = uuidOffset + 16
        let major = Int(manufacturerData[majorOffset]) << 8 | Int(manufacturerData[majorOffset + 1])
        let minor = Int(manufacturerData[majorOffset + 2]) << 8 | Int(manufacturerData[majorOffset + 3])
        
        // 5. Tx Power calibrada a 1 metro
        let txPower = Int(Int8(bitPattern: manufacturerData[majorOffset + 4]))
        
        // Si el Major o Minor es 911 o 0x038F, se clasifica como emergencia activa (1:1 Android y BLE spec)
        let isEmergency = (major == 911 || major == 0x038F || minor == 911 || minor == 0x038F)

        return BeaconPayload(
            idBeacon: idBeacon,
            latitude: latitude,
            longitude: longitude,
            major: major,
            minor: minor,
            txPower: txPower,
            isEmergency: isEmergency
        )
    }
    
    /// Empaqueta las coordenadas e idBeacon en un NSUUID de 16 bytes compatible con iBeacon
    public static func buildProximityUUID(beaconId: String, latitude: Double, longitude: Double) -> UUID? {
        var data = Data(capacity: 16)
        
        // 1. idBeacon (6 bytes)
        let cleanId = beaconId.replacingOccurrences(of: ":", with: "").lowercased()
        var hexData = Data()
        var index = cleanId.startIndex
        while index < cleanId.endIndex {
            let nextIndex = cleanId.index(index, offsetBy: 2, limitedBy: cleanId.endIndex) ?? cleanId.endIndex
            if let byte = UInt8(cleanId[index..<nextIndex], radix: 16) {
                hexData.append(byte)
            }
            index = nextIndex
        }
        
        // Rellenar o recortar a 6 bytes
        if hexData.count < 6 {
            hexData.append(contentsOf: [UInt8](repeating: 0, count: 6 - hexData.count))
        } else if hexData.count > 6 {
            hexData = hexData.prefix(6)
        }
        data.append(hexData)
        
        // 2. Latitud (4 bytes, Float32 Big-Endian)
        var latBits = Float(latitude).bitPattern.bigEndian
        data.append(Data(bytes: &latBits, count: 4))
        
        // 3. Longitud (4 bytes, Float32 Big-Endian)
        var lonBits = Float(longitude).bitPattern.bigEndian
        data.append(Data(bytes: &lonBits, count: 4))
        
        // 4. Magic Bytes: 0xBE 0xAC
        data.append(contentsOf: [0xBE, 0xAC])
        
        guard data.count == 16 else { return nil }
        
        let uuidTuple: uuid_t = (
            data[0], data[1], data[2], data[3],
            data[4], data[5], data[6], data[7],
            data[8], data[9], data[10], data[11],
            data[12], data[13], data[14], data[15]
        )
        return UUID(uuid: uuidTuple)
    }
    
    /// Algoritmo Log-Distance Path Loss para estimar metros a partir del RSSI
    public static func estimateDistance(rssi: Int, txPower: Int = -59) -> Double {
        if rssi == 0 { return -1.0 }
        let ratio = Double(rssi) / Double(txPower)
        if ratio < 1.0 {
            return pow(ratio, 10.0)
        } else {
            return (0.89976) * pow(ratio, 7.7095) + 0.111
        }
    }
}

// MARK: - Emisor de Balizas (BleBeaconBroadcaster)

@Observable
public final class BleBeaconBroadcaster: NSObject, CBPeripheralManagerDelegate, BleBeaconBroadcasterProtocol, @unchecked Sendable {
    
    public private(set) var isBroadcasting: Bool = false
    public var lastBroadcastError: String? = nil
    
    /// Callback invocado si Bluetooth se apaga o pierde permisos mientras el Faro estaba encendido
    @ObservationIgnored
    public var onBroadcastInterrupted: (@Sendable (String) -> Void)?
    
    @ObservationIgnored
    private var peripheralManager: CBPeripheralManager?
    
    @ObservationIgnored
    private var pendingBeaconId: String?
    @ObservationIgnored
    private var pendingLat: Double = 0.0
    @ObservationIgnored
    private var pendingLon: Double = 0.0
    @ObservationIgnored
    private var pendingIsEmergency: Bool = false
    
    @ObservationIgnored
    private var lastAdvertisedLocation: CLLocation?
    @ObservationIgnored
    private var lastAdvertisedDate: Date?
    
    @ObservationIgnored
    private var stateContinuation: CheckedContinuation<CBManagerState, Never>?
    @ObservationIgnored
    private var startAdvContinuation: CheckedContinuation<Int, Never>?
    
    public override init() {
        super.init()
    }
    
    public func startBroadcasting(
        beaconId: String,
        latitude: Double,
        longitude: Double
    ) async -> Bool {
        let code = await startBroadcastingWithCode(
            beaconId: beaconId,
            latitude: latitude,
            longitude: longitude,
            isEmergency: false
        )
        return code == 0
    }
    
    public func startBroadcasting(
        beaconId: String,
        latitude: Double,
        longitude: Double,
        isEmergency: Bool
    ) async -> Bool {
        let code = await startBroadcastingWithCode(
            beaconId: beaconId,
            latitude: latitude,
            longitude: longitude,
            isEmergency: isEmergency
        )
        return code == 0
    }
    
    /// Inicia la emisión BLE devolviendo código de estado 1:1 con Android (`BeaconEmitterManager.startEmitting`):
    /// `0` = éxito, `-1` = Bluetooth apagado / hardware no soportado, `-2` = ID o trama inválida,
    /// `-3` = permiso denegado, `-4` = fallo de CoreBluetooth al anunciar.
    @MainActor
    public func startBroadcastingWithCode(
        beaconId: String,
        latitude: Double,
        longitude: Double,
        isEmergency: Bool = false
    ) async -> Int {
        let cleanId = beaconId
            .replacingOccurrences(of: ":", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        
        let validHexChars = cleanId.filter { $0.isHexDigit }
        guard validHexChars.count >= 2,
              let uuid = BeaconPayload.buildProximityUUID(beaconId: validHexChars, latitude: latitude, longitude: longitude) else {
            self.isBroadcasting = false
            self.lastBroadcastError = "ID de baliza inválido."
            return -2
        }
        
        // Verificar autorización explícita de Bluetooth antes de iniciar
        let auth = CBManager.authorization
        if auth == .denied || auth == .restricted {
            self.isBroadcasting = false
            self.lastBroadcastError = "Permiso de Bluetooth denegado en Ajustes."
            return -3
        }
        
        self.pendingBeaconId = validHexChars
        self.pendingLat = latitude
        self.pendingLon = longitude
        self.pendingIsEmergency = isEmergency
        
        if peripheralManager == nil {
            peripheralManager = CBPeripheralManager(delegate: self, queue: .main)
        }
        
        guard let peripheral = peripheralManager else {
            self.isBroadcasting = false
            self.lastBroadcastError = "No se pudo inicializar el emisor BLE."
            return -2
        }
        
        // Si CoreBluetooth aún está conectando con el daemon (.unknown / .resetting), esperar estado real
        var currentState = peripheral.state
        if currentState == .unknown || currentState == .resetting {
            currentState = await waitForPeripheralState(peripheral)
        }
        
        switch currentState {
        case .poweredOn:
            break
        case .unauthorized:
            self.isBroadcasting = false
            self.lastBroadcastError = "Permiso de Bluetooth denegado."
            return -3
        case .poweredOff:
            self.isBroadcasting = false
            self.lastBroadcastError = "Bluetooth está apagado."
            return -1
        case .unsupported:
            self.isBroadcasting = false
            self.lastBroadcastError = "Este dispositivo no soporta emisión BLE (Faro)."
            return -1
        default:
            self.isBroadcasting = false
            self.lastBroadcastError = "Bluetooth no disponible en este momento."
            return -1
        }
        
        // Construir trama iBeacon 1:1 con Android (major = 1, minor = 911 en emergencia o 1 normal)
        if peripheral.isAdvertising {
            peripheral.stopAdvertising()
        }
        
        let adData = buildAdvertisementDictionary(uuid: uuid, id: validHexChars, isEmergency: isEmergency)
        guard let advertisementData = adData else {
            self.isBroadcasting = false
            self.lastBroadcastError = "No se pudo generar el paquete iBeacon."
            return -2
        }
        
        let resultCode = await startAdvertisingAndWait(peripheral: peripheral, data: advertisementData)
        if resultCode == 0 {
            self.isBroadcasting = true
            self.lastAdvertisedLocation = CLLocation(latitude: latitude, longitude: longitude)
            self.lastAdvertisedDate = Date()
        } else {
            self.isBroadcasting = false
        }
        return resultCode
    }
    
    @MainActor
    private func waitForPeripheralState(_ peripheral: CBPeripheralManager) async -> CBManagerState {
        if peripheral.state != .unknown && peripheral.state != .resetting {
            return peripheral.state
        }
        // Cancelar cualquier espera previa
        if let prev = stateContinuation {
            stateContinuation = nil
            prev.resume(returning: peripheral.state)
        }
        return await withCheckedContinuation { (continuation: CheckedContinuation<CBManagerState, Never>) in
            self.stateContinuation = continuation
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                guard let self = self, let pending = self.stateContinuation else { return }
                self.stateContinuation = nil
                pending.resume(returning: peripheral.state)
            }
        }
    }
    
    @MainActor
    private func startAdvertisingAndWait(peripheral: CBPeripheralManager, data: [String: Any]) async -> Int {
        if let prev = startAdvContinuation {
            startAdvContinuation = nil
            prev.resume(returning: -4)
        }
        return await withCheckedContinuation { (continuation: CheckedContinuation<Int, Never>) in
            self.startAdvContinuation = continuation
            peripheral.startAdvertising(data)
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                guard let self = self, let pending = self.startAdvContinuation else { return }
                self.startAdvContinuation = nil
                // Si CoreBluetooth ya marcó isAdvertising sin disparar error, considerarlo activo
                if peripheral.isAdvertising {
                    self.lastBroadcastError = nil
                    pending.resume(returning: 0)
                } else {
                    self.lastBroadcastError = "Tiempo de espera agotado al encender el Faro BLE."
                    pending.resume(returning: -4)
                }
            }
        }
    }
    
    public func updateCoordinates(latitude: Double, longitude: Double) {
        guard isBroadcasting,
              let peripheral = peripheralManager,
              peripheral.state == .poweredOn,
              let id = pendingBeaconId else {
            return
        }
        
        // 1:1 Android (GpsTrackingService.kt:1056):
        // Solo reiniciar anuncio BLE si el senderista se desplazó > 10m Y pasaron al menos 20s,
        // evitando cortar la señal continuamente con cada pulso del GPS.
        let newLoc = CLLocation(latitude: latitude, longitude: longitude)
        let now = Date()
        if let lastLoc = lastAdvertisedLocation, let lastDate = lastAdvertisedDate {
            let movedMeters = lastLoc.distance(from: newLoc)
            let elapsedSeconds = now.timeIntervalSince(lastDate)
            if movedMeters <= 10.0 || elapsedSeconds < 20.0 {
                return
            }
        }
        
        self.pendingLat = latitude
        self.pendingLon = longitude
        self.lastAdvertisedLocation = newLoc
        self.lastAdvertisedDate = now
        
        guard let uuid = BeaconPayload.buildProximityUUID(beaconId: id, latitude: latitude, longitude: longitude),
              let data = buildAdvertisementDictionary(uuid: uuid, id: id, isEmergency: pendingIsEmergency) else {
            return
        }
        
        if peripheral.isAdvertising {
            peripheral.stopAdvertising()
        }
        peripheral.startAdvertising(data)
    }
    
    public func stopBroadcasting() {
        if let prev = startAdvContinuation {
            startAdvContinuation = nil
            prev.resume(returning: -4)
        }
        peripheralManager?.stopAdvertising()
        self.isBroadcasting = false
        self.lastAdvertisedLocation = nil
        self.lastAdvertisedDate = nil
    }
    
    private func buildAdvertisementDictionary(uuid: UUID, id: String, isEmergency: Bool) -> [String: Any]? {
        let majorValue: CLBeaconMajorValue = 1
        let minorValue: CLBeaconMinorValue = isEmergency ? 911 : 1
        
        #if os(iOS)
        let constraint = CLBeaconIdentityConstraint(uuid: uuid, major: majorValue, minor: minorValue)
        let region = CLBeaconRegion(
            beaconIdentityConstraint: constraint,
            identifier: "com.gaspar.rutassendero.beacon"
        )
        let dict = region.peripheralData(withMeasuredPower: NSNumber(value: -59))
        var result: [String: Any] = [:]
        for (key, value) in dict {
            if let k = key as? String {
                result[k] = value
            }
        }
        return result.isEmpty ? nil : result
        #else
        return [
            CBAdvertisementDataLocalNameKey: "RutasBeacon-\(id.prefix(6))"
        ]
        #endif
    }
    
    // MARK: - CBPeripheralManagerDelegate
    
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if let pendingState = stateContinuation, peripheral.state != .unknown && peripheral.state != .resetting {
            stateContinuation = nil
            pendingState.resume(returning: peripheral.state)
            return
        }
        
        if peripheral.state != .poweredOn && isBroadcasting {
            self.isBroadcasting = false
            let msg: String
            switch peripheral.state {
            case .poweredOff:
                msg = "Faro apagado: se desactivó el Bluetooth del iPhone."
            case .unauthorized:
                msg = "Faro apagado: permiso de Bluetooth revocado."
            default:
                msg = "Bluetooth no disponible (Estado: \(peripheral.state.rawValue))."
            }
            self.lastBroadcastError = msg
            self.onBroadcastInterrupted?(msg)
        }
    }
    
    public func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            lastBroadcastError = error.localizedDescription
            isBroadcasting = false
            print("⚠️ [BleBroadcaster] Error al emitir baliza: \(error.localizedDescription)")
            if let cont = startAdvContinuation {
                startAdvContinuation = nil
                cont.resume(returning: -4)
            }
        } else {
            lastBroadcastError = nil
            isBroadcasting = true
            print("📡 [BleBroadcaster] Baliza iBeacon emitiendo con éxito")
            if let cont = startAdvContinuation {
                startAdvContinuation = nil
                cont.resume(returning: 0)
            }
        }
    }
}

// MARK: - Escáner de Balizas de Compañeros (BleBeaconScanner)

@Observable
public final class BleBeaconScanner: NSObject, CBCentralManagerDelegate, BleBeaconScannerProtocol, @unchecked Sendable {
    
    public private(set) var isScanning: Bool = false
    public var lastScannerError: String? = nil
    
    @ObservationIgnored
    private var centralManager: CBCentralManager?
    
    @ObservationIgnored
    private var onPeerDetectedCallback: (@Sendable (PeerBeaconData) -> Void)?
    
    public override init() {
        super.init()
    }
    
    public func startScanning(onPeerDetected: @escaping @Sendable (PeerBeaconData) -> Void) -> Bool {
        self.onPeerDetectedCallback = onPeerDetected
        self.isScanning = true
        
        if centralManager == nil {
            centralManager = CBCentralManager(delegate: self, queue: nil)
        } else if centralManager?.state == .poweredOn {
            startCentralScan()
        }
        return true
    }
    
    public func stopScanning() {
        centralManager?.stopScan()
        self.isScanning = false
        self.onPeerDetectedCallback = nil
    }
    
    private func startCentralScan() {
        guard let central = centralManager, central.state == .poweredOn else {
            return
        }
        // En iOS, escanear paquetes con opciones de duplicados para actualizar telemetría en vivo
        let options: [String: Any] = [
            CBCentralManagerScanOptionAllowDuplicatesKey: true
        ]
        central.scanForPeripherals(withServices: nil, options: options)
        print("🔍 [BleScanner] Escaneo de compañeros senderistas activado")
    }
    
    // MARK: - CBCentralManagerDelegate
    
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn && isScanning {
            startCentralScan()
        } else if central.state != .poweredOn {
            lastScannerError = "Bluetooth apagado o sin permisos"
        }
    }
    
    public func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data else {
            return
        }
        
        guard let payload = BeaconPayload.parse(manufacturerData: manufacturerData) else {
            return
        }
        
        let rssiInt = RSSI.intValue
        let estimatedMeters = BeaconPayload.estimateDistance(rssi: rssiInt, txPower: payload.txPower)
        
        let peerData = PeerBeaconData(
            beaconId: payload.idBeacon,
            latitude: payload.latitude,
            longitude: payload.longitude,
            rssi: rssiInt,
            txPower: payload.txPower,
            estimatedDistanceMeters: estimatedMeters,
            lastDetectedTimestamp: Date(),
            isEmergency: payload.isEmergency
        )
        
        onPeerDetectedCallback?(peerData)
    }
}
