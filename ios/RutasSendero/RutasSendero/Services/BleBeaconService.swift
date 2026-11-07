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

    /// Parsea los bytes de Manufacturer Data recibidos por CoreBluetooth (soporta cualquier Company ID: 0x004C Apple, 0x0075 Samsung, etc.)
    public static func parse(manufacturerData: Data) -> BeaconPayload? {
        let bytes = [UInt8](manufacturerData)
        
        // 1. Búsqueda flexible 1:1 con Android parseBeaconBruteForce:
        // Busca la cabecera iBeacon (0x02, 0x15) seguida 14 bytes después por la firma mágica (0xBE, 0xAC)
        // sin importar el Company ID de 2 bytes que anteponga el fabricante del teléfono emisor.
        if bytes.count >= 23 {
            for i in 0...(bytes.count - 23) {
                if bytes[i] == 0x02 && bytes[i + 1] == 0x15 &&
                   bytes[i + 16] == 0xBE && bytes[i + 17] == 0xAC {
                    let uuidOffset = i + 2
                    let idBeacon = bytes[uuidOffset..<(uuidOffset + 6)]
                        .map { String(format: "%02x", $0) }
                        .joined()
                    
                    let latBits = (UInt32(bytes[uuidOffset + 6]) << 24) |
                                  (UInt32(bytes[uuidOffset + 7]) << 16) |
                                  (UInt32(bytes[uuidOffset + 8]) << 8) |
                                  UInt32(bytes[uuidOffset + 9])
                    let latitude = Double(Float(bitPattern: latBits))
                    
                    let lonBits = (UInt32(bytes[uuidOffset + 10]) << 24) |
                                  (UInt32(bytes[uuidOffset + 11]) << 16) |
                                  (UInt32(bytes[uuidOffset + 12]) << 8) |
                                  UInt32(bytes[uuidOffset + 13])
                    let longitude = Double(Float(bitPattern: lonBits))
                    
                    let majorOffset = uuidOffset + 16
                    let major = (Int(bytes[majorOffset]) << 8) | Int(bytes[majorOffset + 1])
                    let minor = (Int(bytes[majorOffset + 2]) << 8) | Int(bytes[majorOffset + 3])
                    let txPower = Int(Int8(bitPattern: bytes[majorOffset + 4]))
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
            }
        }
        
        // 2. Fallback: paquete compacto de 16 bytes con firma mágica 0xBE 0xAC al final
        if bytes.count >= 16 {
            for i in 0...(bytes.count - 16) {
                if bytes[i + 14] == 0xBE && bytes[i + 15] == 0xAC {
                    return parseRawUUIDBytes(Array(bytes[i..<(i + 16)]), isEmergency: false)
                }
            }
        }
        
        return nil
    }
    
    /// Parsea un diccionario completo de anuncio CoreBluetooth (ManufacturerData, ServiceUUIDs y ServiceData).
    /// Permite recepción cruzada Android <-> iOS e iOS <-> iOS incluso cuando iOS oculta paquetes iBeacon 0x004C.
    public static func parse(advertisementData: [String: Any]) -> BeaconPayload? {
        if let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data,
           let payload = parse(manufacturerData: manufacturerData) {
            return payload
        }
        
        var allServiceUUIDs: [CBUUID] = []
        if let uuids = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            allServiceUUIDs.append(contentsOf: uuids)
        }
        if let overflowUUIDs = advertisementData[CBAdvertisementDataOverflowServiceUUIDsKey] as? [CBUUID] {
            allServiceUUIDs.append(contentsOf: overflowUUIDs)
        }
        if !allServiceUUIDs.isEmpty, let payload = parse(serviceUUIDs: allServiceUUIDs) {
            return payload
        }
        
        if let serviceDataMap = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] {
            let emergencyFlag = serviceDataMap.keys.contains { $0.uuidString.uppercased() == "B911" }
            for (_, data) in serviceDataMap {
                let bytes = [UInt8](data)
                if bytes.count >= 16 && bytes[14] == 0xBE && bytes[15] == 0xAC {
                    return parseRawUUIDBytes(Array(bytes[0..<16]), isEmergency: emergencyFlag)
                }
            }
        }
        
        return nil
    }
    
    /// Extrae el BeaconPayload desde CBUUIDs de 128 bits emitidos por otros dispositivos RutasSendero (iOS <-> iOS).
    public static func parse(serviceUUIDs: [CBUUID]) -> BeaconPayload? {
        let isEmergency = serviceUUIDs.contains { $0.uuidString.uppercased() == "B911" }
        for cbuuid in serviceUUIDs {
            let raw = [UInt8](cbuuid.data)
            if raw.count == 16 && raw[14] == 0xBE && raw[15] == 0xAC {
                return parseRawUUIDBytes(raw, isEmergency: isEmergency)
            }
        }
        return nil
    }
    
    private static func parseRawUUIDBytes(_ bytes: [UInt8], isEmergency: Bool) -> BeaconPayload? {
        guard bytes.count == 16, bytes[14] == 0xBE, bytes[15] == 0xAC else { return nil }
        let idBeacon = bytes[0..<6].map { String(format: "%02x", $0) }.joined()
        let latBits = (UInt32(bytes[6]) << 24) | (UInt32(bytes[7]) << 16) | (UInt32(bytes[8]) << 8) | UInt32(bytes[9])
        let lonBits = (UInt32(bytes[10]) << 24) | (UInt32(bytes[11]) << 16) | (UInt32(bytes[12]) << 8) | UInt32(bytes[13])
        let latitude = Double(Float(bitPattern: latBits))
        let longitude = Double(Float(bitPattern: lonBits))
        guard !latitude.isNaN && !longitude.isNaN && abs(latitude) <= 90.0 && abs(longitude) <= 180.0 else {
            return nil
        }
        return BeaconPayload(
            idBeacon: idBeacon,
            latitude: latitude,
            longitude: longitude,
            major: 1,
            minor: isEmergency ? 911 : 1,
            txPower: -59,
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
    private var hasRealLocationFix: Bool = false
    @ObservationIgnored
    private var dualModeTask: Task<Void, Never>?
    @ObservationIgnored
    private var isCyclingAdvertisement: Bool = false
    
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
            isEmergency: false,
            hasRealLocation: true
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
            isEmergency: isEmergency,
            hasRealLocation: true
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
        isEmergency: Bool = false,
        hasRealLocation: Bool = true
    ) async -> Int {
        dualModeTask?.cancel()
        dualModeTask = nil
        isCyclingAdvertisement = false
        
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
        self.hasRealLocationFix = hasRealLocation
        
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
            self.lastAdvertisedLocation = hasRealLocation ? CLLocation(latitude: latitude, longitude: longitude) : nil
            self.lastAdvertisedDate = Date()
            self.startDualFrameLoop()
        } else {
            self.isBroadcasting = false
        }
        return resultCode
    }
    
    /// Alterna ráfagas entre iBeacon estándar (0x004C para receptores Android) y CBUUID de 128 bits
    /// (visible sin bloqueo de CoreBluetooth para receptores iOS <-> iOS en primer y segundo plano).
    @MainActor
    private func startDualFrameLoop() {
        dualModeTask?.cancel()
        dualModeTask = Task { @MainActor [weak self] in
            var emitServiceUUIDFrame = false
            while !Task.isCancelled {
                let sleepNs: UInt64 = emitServiceUUIDFrame ? 1_200_000_000 : 2_000_000_000
                try? await Task.sleep(nanoseconds: sleepNs)
                guard !Task.isCancelled,
                      let self = self,
                      self.isBroadcasting,
                      let peripheral = self.peripheralManager,
                      peripheral.state == .poweredOn,
                      let id = self.pendingBeaconId,
                      let uuid = BeaconPayload.buildProximityUUID(beaconId: id, latitude: self.pendingLat, longitude: self.pendingLon) else {
                    break
                }
                
                emitServiceUUIDFrame.toggle()
                self.isCyclingAdvertisement = true
                if peripheral.isAdvertising {
                    peripheral.stopAdvertising()
                }
                
                if emitServiceUUIDFrame {
                    let gattDict = self.buildServiceUUIDAdvertisementDictionary(uuid: uuid, isEmergency: self.pendingIsEmergency)
                    peripheral.startAdvertising(gattDict)
                } else if let ibeaconDict = self.buildAdvertisementDictionary(uuid: uuid, id: id, isEmergency: self.pendingIsEmergency) {
                    peripheral.startAdvertising(ibeaconDict)
                }
            }
        }
    }
    
    private func buildServiceUUIDAdvertisementDictionary(uuid: UUID, isEmergency: Bool) -> [String: Any] {
        let statusUUID = CBUUID(string: isEmergency ? "B911" : "B001")
        return [
            CBAdvertisementDataServiceUUIDsKey: [CBUUID(nsuuid: uuid), statusUUID]
        ]
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
        
        // 1:1 Android (GpsTrackingService.kt:1097):
        // Si no había fix real al encender el Faro, actualizar de inmediato con la primera lectura GPS válida;
        // después, solo reiniciar anuncio si se desplazó > 10m Y pasaron al menos 20s.
        let newLoc = CLLocation(latitude: latitude, longitude: longitude)
        let now = Date()
        if hasRealLocationFix, let lastLoc = lastAdvertisedLocation, let lastDate = lastAdvertisedDate {
            let movedMeters = lastLoc.distance(from: newLoc)
            let elapsedSeconds = now.timeIntervalSince(lastDate)
            if movedMeters <= 10.0 || elapsedSeconds < 20.0 {
                return
            }
        }
        
        self.hasRealLocationFix = true
        self.pendingLat = latitude
        self.pendingLon = longitude
        self.lastAdvertisedLocation = newLoc
        self.lastAdvertisedDate = now
        
        guard let uuid = BeaconPayload.buildProximityUUID(beaconId: id, latitude: latitude, longitude: longitude),
              let data = buildAdvertisementDictionary(uuid: uuid, id: id, isEmergency: pendingIsEmergency) else {
            return
        }
        
        self.isCyclingAdvertisement = true
        if peripheral.isAdvertising {
            peripheral.stopAdvertising()
        }
        peripheral.startAdvertising(data)
    }
    
    public func stopBroadcasting() {
        dualModeTask?.cancel()
        dualModeTask = nil
        isCyclingAdvertisement = false
        if let prev = startAdvContinuation {
            startAdvContinuation = nil
            prev.resume(returning: -4)
        }
        peripheralManager?.stopAdvertising()
        self.isBroadcasting = false
        self.lastAdvertisedLocation = nil
        self.lastAdvertisedDate = nil
        self.hasRealLocationFix = false
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
            dualModeTask?.cancel()
            dualModeTask = nil
            isCyclingAdvertisement = false
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
        if let cont = startAdvContinuation {
            startAdvContinuation = nil
            if let error = error {
                lastBroadcastError = error.localizedDescription
                isBroadcasting = false
                print("⚠️ [BleBroadcaster] Error al emitir baliza: \(error.localizedDescription)")
                cont.resume(returning: -4)
            } else {
                lastBroadcastError = nil
                isBroadcasting = true
                print("📡 [BleBroadcaster] Baliza iBeacon emitiendo con éxito")
                cont.resume(returning: 0)
            }
            return
        }
        
        // Eventos de rotación dual de trama (sin afectar la continuación inicial)
        if let error = error {
            print("⚠️ [BleBroadcaster] Aviso en cambio de trama BLE: \(error.localizedDescription)")
        }
        isCyclingAdvertisement = false
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
    private var wantsScanning: Bool = false
    
    @ObservationIgnored
    private var onPeerDetectedCallback: (@Sendable (PeerBeaconData) -> Void)?
    
    public override init() {
        super.init()
    }
    
    public func startScanning(onPeerDetected: @escaping @Sendable (PeerBeaconData) -> Void) -> Bool {
        self.onPeerDetectedCallback = onPeerDetected
        self.wantsScanning = true
        
        if centralManager == nil {
            centralManager = CBCentralManager(delegate: self, queue: .main)
        } else if centralManager?.state == .poweredOn {
            startCentralScan()
        } else {
            self.isScanning = false
        }
        return true
    }
    
    /// Re-arma activamente el escaneo CoreBluetooth al abrir el mapa/monitoreo de balizas o volver a primer plano.
    public func refreshScan() {
        guard wantsScanning else { return }
        if centralManager == nil {
            centralManager = CBCentralManager(delegate: self, queue: .main)
            return
        }
        guard let central = centralManager else { return }
        if central.state == .poweredOn {
            central.stopScan()
            startCentralScan()
        } else {
            self.isScanning = false
        }
    }
    
    public func stopScanning() {
        wantsScanning = false
        centralManager?.stopScan()
        self.isScanning = false
        self.onPeerDetectedCallback = nil
    }
    
    private func startCentralScan() {
        guard let central = centralManager, central.state == .poweredOn else {
            self.isScanning = false
            return
        }
        // En iOS, escanear paquetes con opciones de duplicados para actualizar telemetría y mapa en vivo
        let options: [String: Any] = [
            CBCentralManagerScanOptionAllowDuplicatesKey: true
        ]
        central.scanForPeripherals(withServices: nil, options: options)
        self.isScanning = true
        self.lastScannerError = nil
        print("🔍 [BleScanner] Escaneo de compañeros senderistas activado")
    }
    
    // MARK: - CBCentralManagerDelegate
    
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn && wantsScanning {
            startCentralScan()
        } else if central.state != .poweredOn {
            self.isScanning = false
            lastScannerError = "Bluetooth apagado o sin permisos"
        }
    }
    
    public func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let payload = BeaconPayload.parse(advertisementData: advertisementData) else {
            return
        }
        
        // En CoreBluetooth, RSSI == 127 significa lectura de potencia no disponible en ese pulso; usar -70 dBm nominal
        let rawRssi = RSSI.intValue
        let rssiInt = (rawRssi == 127 || rawRssi == 0) ? -70 : rawRssi
        let estimatedMeters = BeaconPayload.estimateDistance(rssi: rssiInt, txPower: payload.txPower)
        
        let peerData = PeerBeaconData(
            beaconId: payload.idBeacon,
            latitude: payload.latitude,
            longitude: payload.longitude,
            rssi: rssiInt,
            txPower: payload.txPower,
            estimatedDistanceMeters: max(0.1, estimatedMeters),
            lastDetectedTimestamp: Date(),
            isEmergency: payload.isEmergency
        )
        
        onPeerDetectedCallback?(peerData)
    }
}
