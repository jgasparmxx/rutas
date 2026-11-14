//
//  SoundAlertsSheetView.swift
//  RutasSendero
//
//  Diálogo de Silbatos, Alertas de Emergencia y Linterna (1:1 con Android SenderistaSoundsDialog).
//

import SwiftUI
import AVFoundation
import AudioToolbox

// MARK: - Sintetizador Acústico de Silbato de Senderismo (Funciona Offline e Ignora Switch de Silencio en Emergencias)

@MainActor
final class HikerWhistlePlayer {
    static let shared = HikerWhistlePlayer()
    
    private var patternTask: Task<Void, Never>?
    private var activePlayer: AVAudioPlayer?
    private(set) var isRepeatingEmergency: Bool = false
    
    // MARK: - Alarma 911 de Compañero BLE (1:1 Android GpsTrackingService.startEmergencyPatternInternal)
    private var peerAlarmTask: Task<Void, Never>?
    private var peerAlarmPlayer: AVAudioPlayer?
    private(set) var isPeerEmergencyAlarmPlaying: Bool = false
    
    // MARK: - Pitido Doppler, Chimes de Llegada y Keep-Alive en Bolsillo
    private var dopplerPlayer: AVAudioPlayer?
    private var chimeTask: Task<Void, Never>?
    private var chimePlayer: AVAudioPlayer?
    private var silentKeepAlivePlayer: AVAudioPlayer?
    private(set) var isBackgroundNavigationActive: Bool = false
    
    private lazy var dopplerPipWavData: Data? = Self.makePureToneWavData(durationMs: 55, frequencyHz: 1350.0)
    private lazy var alarmShortWavData: Data? = Self.makeWhistleWavData(durationMs: 100, frequencyHz: 2900.0)
    private lazy var alarmLongWavData: Data? = Self.makeWhistleWavData(durationMs: 400, frequencyHz: 2900.0)
    private lazy var silentOneSecWavData: Data? = Self.makeSilentWavData(durationMs: 1000)
    
    private init() {
        #if os(iOS)
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let userInfo = notification.userInfo,
                  let typeVal = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: typeVal) else {
                return
            }
            if type == .ended {
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    if self.isBackgroundNavigationActive || self.isPeerEmergencyAlarmPlaying || self.isRepeatingEmergency {
                        self.configureAudioSession()
                    }
                    if self.isBackgroundNavigationActive {
                        self.ensureSilentKeepAlivePlaying()
                    }
                }
            }
        }
        #endif
    }
    
    /// Reproduce una secuencia única de `(duración Pitido ms, pausa ms)` (1:1 Android GpsTrackingService.playSoundPattern)
    func playOnce(pattern: [(toneMs: Int, pauseMs: Int)], frequencyHz: Double = 2750.0) {
        stop()
        isRepeatingEmergency = false
        patternTask = Task { @MainActor in
            configureAudioSession()
            await playSequence(pattern: pattern, frequencyHz: frequencyHz)
        }
    }
    
    /// Reproduce un patrón de emergencia en bucle continuo hasta que se detenga (1:1 Android startEmergencyPattern)
    func startEmergencyLoop(pattern: [(toneMs: Int, pauseMs: Int)], frequencyHz: Double = 2850.0, onStateChange: (@MainActor (Bool) -> Void)? = nil) {
        stop()
        isRepeatingEmergency = true
        onStateChange?(true)
        patternTask = Task { @MainActor in
            configureAudioSession()
            while !Task.isCancelled {
                await playSequence(pattern: pattern, frequencyHz: frequencyHz)
                if Task.isCancelled { break }
                try? await Task.sleep(nanoseconds: 650_000_000)
            }
            self.isRepeatingEmergency = false
            onStateChange?(false)
        }
    }
    
    func stop() {
        patternTask?.cancel()
        patternTask = nil
        activePlayer?.stop()
        activePlayer = nil
        isRepeatingEmergency = false
    }
    
    /// Inicia el bucle de alarma acústica 911 cuando un compañero cercano solicita auxilio (1:1 Android: [(100,50),(100,50),(400,100)])
    func startPeerEmergency911Alarm() {
        guard !isPeerEmergencyAlarmPlaying else { return }
        isPeerEmergencyAlarmPlaying = true
        peerAlarmTask?.cancel()
        peerAlarmTask = Task { @MainActor in
            configureAudioSession()
            let steps: [(data: Data?, toneMs: Int, pauseMs: Int)] = [
                (self.alarmShortWavData, 100, 50),
                (self.alarmShortWavData, 100, 50),
                (self.alarmLongWavData, 400, 100)
            ]
            while !Task.isCancelled && self.isPeerEmergencyAlarmPlaying {
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                for step in steps {
                    guard !Task.isCancelled && self.isPeerEmergencyAlarmPlaying else { break }
                    if let data = step.data, let player = try? AVAudioPlayer(data: data) {
                        player.volume = 1.0
                        player.prepareToPlay()
                        self.peerAlarmPlayer = player
                        player.play()
                    } else {
                        AudioServicesPlaySystemSound(1005)
                    }
                    try? await Task.sleep(nanoseconds: UInt64(step.toneMs) * 1_000_000)
                    self.peerAlarmPlayer?.stop()
                    self.peerAlarmPlayer = nil
                    guard !Task.isCancelled && self.isPeerEmergencyAlarmPlaying else { break }
                    if step.pauseMs > 0 {
                        try? await Task.sleep(nanoseconds: UInt64(step.pauseMs) * 1_000_000)
                    }
                }
                if Task.isCancelled || !self.isPeerEmergencyAlarmPlaying { break }
                try? await Task.sleep(nanoseconds: 550_000_000)
            }
            self.peerAlarmPlayer?.stop()
            self.peerAlarmPlayer = nil
            self.isPeerEmergencyAlarmPlaying = false
        }
    }
    
    /// Silencia la alarma acústica 911 de compañeros cercanos
    func stopPeerEmergency911Alarm() {
        isPeerEmergencyAlarmPlaying = false
        peerAlarmTask?.cancel()
        peerAlarmTask = nil
        peerAlarmPlayer?.stop()
        peerAlarmPlayer = nil
    }
    
    /// Pitido corto tipo sonar para proximidad Doppler en Modo Guía (funciona con pantalla apagada y switch de silencio activo)
    func playDopplerPip() {
        guard !isPeerEmergencyAlarmPlaying && !isRepeatingEmergency else { return }
        configureAudioSession()
        if let player = dopplerPlayer {
            player.stop()
            player.currentTime = 0
            player.play()
            return
        }
        if let data = dopplerPipWavData, let player = try? AVAudioPlayer(data: data) {
            player.volume = 0.92
            player.prepareToPlay()
            self.dopplerPlayer = player
            player.play()
        }
    }
    
    /// Fanfarria / chime acústico al llegar a un punto de control o destino final (1:1 Android GpsTrackingService.kt:832, 839)
    func playWaypointChime(isFinal: Bool) {
        guard !isPeerEmergencyAlarmPlaying && !isRepeatingEmergency else { return }
        chimeTask?.cancel()
        let pattern: [(toneMs: Int, pauseMs: Int)] = isFinal
            ? [(150, 50), (150, 50), (150, 50), (400, 100), (200, 50), (600, 200)]
            : [(150, 50), (150, 50), (300, 100)]
        let baseFreq = isFinal ? 1760.0 : 1580.0
        chimeTask = Task { @MainActor in
            configureAudioSession()
            for (idx, step) in pattern.enumerated() {
                guard !Task.isCancelled else { return }
                let freq = baseFreq + Double(idx * 90)
                if let data = Self.makePureToneWavData(durationMs: step.toneMs, frequencyHz: freq),
                   let player = try? AVAudioPlayer(data: data) {
                    player.volume = 0.95
                    player.prepareToPlay()
                    self.chimePlayer = player
                    player.play()
                }
                try? await Task.sleep(nanoseconds: UInt64(step.toneMs) * 1_000_000)
                self.chimePlayer?.stop()
                self.chimePlayer = nil
                guard !Task.isCancelled else { return }
                if step.pauseMs > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(step.pauseMs) * 1_000_000)
                }
            }
        }
    }
    
    /// Mantiene viva la sesión de audio y los temporizadores asíncronos (Doppler / TTS) mientras Modo Guía o Grabación están activos en el bolsillo
    func setBackgroundNavigationActive(_ active: Bool) {
        isBackgroundNavigationActive = active
        if active {
            configureAudioSession()
            ensureSilentKeepAlivePlaying()
        } else {
            silentKeepAlivePlayer?.stop()
            silentKeepAlivePlayer = nil
        }
    }
    
    private func ensureSilentKeepAlivePlaying() {
        if let existing = silentKeepAlivePlayer, existing.isPlaying {
            return
        }
        guard let data = silentOneSecWavData, let player = try? AVAudioPlayer(data: data) else { return }
        player.numberOfLoops = -1
        player.volume = 0.01
        player.prepareToPlay()
        self.silentKeepAlivePlayer = player
        player.play()
    }
    
    private func configureAudioSession() {
        #if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.duckOthers, .mixWithOthers])
            try session.setActive(true)
        } catch {
            print("⚠️ [HikerWhistlePlayer] Error configurando AVAudioSession: \(error.localizedDescription)")
        }
        #endif
    }
    
    private func playSequence(pattern: [(toneMs: Int, pauseMs: Int)], frequencyHz: Double) async {
        for step in pattern {
            guard !Task.isCancelled else { return }
            if let wavData = Self.makeWhistleWavData(durationMs: step.toneMs, frequencyHz: frequencyHz),
               let player = try? AVAudioPlayer(data: wavData) {
                player.volume = 1.0
                player.prepareToPlay()
                self.activePlayer = player
                player.play()
            } else {
                AudioServicesPlaySystemSound(1005)
            }
            
            let toneNs = UInt64(max(20, step.toneMs)) * 1_000_000
            try? await Task.sleep(nanoseconds: toneNs)
            activePlayer?.stop()
            activePlayer = nil
            
            guard !Task.isCancelled else { return }
            if step.pauseMs > 0 {
                let pauseNs = UInt64(step.pauseMs) * 1_000_000
                try? await Task.sleep(nanoseconds: pauseNs)
            }
        }
    }
    
    /// Genera en memoria un buffer WAV PCM 16-bit mono con armónicos de silbato de rescate (alta penetración en montaña)
    private static func makeWhistleWavData(durationMs: Int, frequencyHz: Double) -> Data? {
        let sampleRate = 44100
        let totalSamples = max(1, (sampleRate * durationMs) / 1000)
        let fadeSamples = min(totalSamples / 6, 440) // 10ms rampa suave anti-click
        
        var pcm = Data(capacity: totalSamples * 2)
        for i in 0..<totalSamples {
            let t = Double(i) / Double(sampleRate)
            // Silbato dual tipo rescate (dos cámaras ligeramente desafinadas + segundo armónico)
            let wave1 = sin(2.0 * .pi * frequencyHz * t)
            let wave2 = sin(2.0 * .pi * (frequencyHz * 1.035) * t)
            let harmonic = 0.25 * sin(2.0 * .pi * (frequencyHz * 2.0) * t)
            var envelope = 1.0
            if i < fadeSamples {
                envelope = Double(i) / Double(max(1, fadeSamples))
            } else if i > totalSamples - fadeSamples {
                envelope = Double(totalSamples - i) / Double(max(1, fadeSamples))
            }
            let normalized = ((wave1 * 0.45) + (wave2 * 0.40) + (harmonic * 0.15)) * envelope
            let sample = Int16(max(-32767.0, min(32767.0, normalized * 31000.0)))
            var leSample = sample.littleEndian
            withUnsafeBytes(of: &leSample) { pcm.append(contentsOf: $0) }
        }
        
        let dataSize = UInt32(pcm.count)
        let chunkSize = UInt32(36) + dataSize
        let byteRate = UInt32(sampleRate * 2)
        let blockAlign: UInt16 = 2
        let bitsPerSample: UInt16 = 16
        
        var header = Data()
        header.append(contentsOf: Array("RIFF".utf8))
        header.append(uint32LE(chunkSize))
        header.append(contentsOf: Array("WAVE".utf8))
        header.append(contentsOf: Array("fmt ".utf8))
        header.append(uint32LE(16)) // PCM
        header.append(uint16LE(1))  // AudioFormat = 1
        header.append(uint16LE(1))  // NumChannels = 1 (Mono)
        header.append(uint32LE(UInt32(sampleRate)))
        header.append(uint32LE(byteRate))
        header.append(uint16LE(blockAlign))
        header.append(uint16LE(bitsPerSample))
        header.append(contentsOf: Array("data".utf8))
        header.append(uint32LE(dataSize))
        header.append(pcm)
        return header
    }
    
    /// Genera un tono limpio tipo sonar para pitidos Doppler y chimes de llegada
    private static func makePureToneWavData(durationMs: Int, frequencyHz: Double) -> Data? {
        let sampleRate = 22050
        let totalSamples = max(1, (sampleRate * durationMs) / 1000)
        let fadeSamples = min(totalSamples / 4, 160)
        
        var pcm = Data(capacity: totalSamples * 2)
        for i in 0..<totalSamples {
            let t = Double(i) / Double(sampleRate)
            let wave = sin(2.0 * .pi * frequencyHz * t) + 0.18 * sin(2.0 * .pi * (frequencyHz * 2.0) * t)
            var envelope = 1.0
            if i < fadeSamples {
                envelope = Double(i) / Double(max(1, fadeSamples))
            } else if i > totalSamples - fadeSamples {
                envelope = Double(totalSamples - i) / Double(max(1, fadeSamples))
            }
            let normalized = (wave / 1.18) * envelope
            let sample = Int16(max(-32767.0, min(32767.0, normalized * 28500.0)))
            var leSample = sample.littleEndian
            withUnsafeBytes(of: &leSample) { pcm.append(contentsOf: $0) }
        }
        return wrapPcmInWavHeader(pcm: pcm, sampleRate: sampleRate)
    }
    
    /// Genera un buffer PCM silencioso ultraligero (8 kHz) para mantener activa la sesión de audio en bolsillo
    private static func makeSilentWavData(durationMs: Int) -> Data? {
        let sampleRate = 8000
        let totalSamples = max(1, (sampleRate * durationMs) / 1000)
        let pcm = Data(count: totalSamples * 2)
        return wrapPcmInWavHeader(pcm: pcm, sampleRate: sampleRate)
    }
    
    private static func wrapPcmInWavHeader(pcm: Data, sampleRate: Int) -> Data {
        let dataSize = UInt32(pcm.count)
        let chunkSize = UInt32(36) + dataSize
        let byteRate = UInt32(sampleRate * 2)
        let blockAlign: UInt16 = 2
        let bitsPerSample: UInt16 = 16
        
        var header = Data(capacity: 44 + pcm.count)
        header.append(contentsOf: Array("RIFF".utf8))
        header.append(uint32LE(chunkSize))
        header.append(contentsOf: Array("WAVE".utf8))
        header.append(contentsOf: Array("fmt ".utf8))
        header.append(uint32LE(16))
        header.append(uint16LE(1))
        header.append(uint16LE(1))
        header.append(uint32LE(UInt32(sampleRate)))
        header.append(uint32LE(byteRate))
        header.append(uint16LE(blockAlign))
        header.append(uint16LE(bitsPerSample))
        header.append(contentsOf: Array("data".utf8))
        header.append(uint32LE(dataSize))
        header.append(pcm)
        return header
    }
    
    private static func uint32LE(_ value: UInt32) -> Data {
        var v = value.littleEndian
        return withUnsafeBytes(of: &v) { Data($0) }
    }
    
    private static func uint16LE(_ value: UInt16) -> Data {
        var v = value.littleEndian
        return withUnsafeBytes(of: &v) { Data($0) }
    }
}

// MARK: - Vista Modal 1:1 con Android SenderistaSoundsDialog

public struct SoundAlertsSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var activeDescription: String? = nil
    @State private var isFlashlightOn: Bool = false
    @State private var isEmergencySoundLooping: Bool = false
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }
            
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    // Título del Diálogo (1:1 Android)
                    HStack(spacing: 8) {
                        Spacer()
                        Image("WhistleBright")
                            .resizable()
                            .scaledToFill()
                            .frame(width: 34, height: 34)
                            .clipShape(Circle())
                            .overlay(
                                Circle()
                                    .stroke(Color(red: 255/255, green: 152/255, blue: 0/255), lineWidth: 1.5)
                            )
                        
                        Text("Silbatos y Alertas")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                        Spacer()
                    }
                    .padding(.bottom, 14)
                    
                    // Sección 1: Comunicación Senderista (1:1 Android)
                    Text("Comunicación Senderista:")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(AppTheme.accent)
                        .padding(.bottom, 6)
                    
                    VStack(spacing: 6) {
                        // 1. Pedir Ubicación (1 pitido corto)
                        outlinedCommButton(title: "Pedir Ubicación") {
                            activeDescription = "Pedir Ubicación: 1 pitido corto (indica o solicita ubicación)"
                            isEmergencySoundLooping = false
                            HikerWhistlePlayer.shared.playOnce(pattern: [(200, 100)])
                        }
                        
                        // 2. Todo bien (1 pitido largo)
                        outlinedCommButton(title: "Todo bien") {
                            activeDescription = "Todo bien: 1 pitido largo (respuesta afirmativa / todo en orden)"
                            isEmergencySoundLooping = false
                            HikerWhistlePlayer.shared.playOnce(pattern: [(2000, 100)])
                        }
                        
                        // 3. Ven a mí (2 pitidos cortos)
                        outlinedCommButton(title: "Ven a mí") {
                            activeDescription = "Ven a mí: 2 pitidos cortos (llamado para aproximarse)"
                            isEmergencySoundLooping = false
                            HikerWhistlePlayer.shared.playOnce(pattern: [(200, 100), (200, 100)])
                        }
                    }
                    
                    Spacer().frame(height: 16)
                    
                    // Sección 2: Señal de Emergencia (1:1 Android)
                    Text("Señal de Emergencia:")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(AppTheme.alert)
                        .padding(.bottom, 6)
                    
                    VStack(spacing: 6) {
                        // 4. Solicito ayuda (5 pitidos largos de auxilio)
                        emergencyButton(
                            title: "Solicito ayuda",
                            systemIcon: "questionmark.circle.fill",
                            bgColor: Color(red: 245/255, green: 158/255, blue: 11/255) // #F59E0B
                        ) {
                            activeDescription = "Solicito ayuda: 5 pitidos largos repetitivos de auxilio"
                            HikerWhistlePlayer.shared.startEmergencyLoop(
                                pattern: Array(repeating: (800, 400), count: 5),
                                onStateChange: { isEmergencySoundLooping = $0 }
                            )
                        }
                        
                        // 5. Emergencia (10 pitidos cortos continuos)
                        emergencyButton(
                            title: "Emergencia",
                            systemIcon: "exclamationmark.triangle.fill",
                            bgColor: Color(red: 239/255, green: 68/255, blue: 68/255) // #EF4444
                        ) {
                            activeDescription = "Emergencia: 10 pitidos cortos continuos"
                            HikerWhistlePlayer.shared.startEmergencyLoop(
                                pattern: Array(repeating: (200, 200), count: 10),
                                onStateChange: { isEmergencySoundLooping = $0 }
                            )
                        }
                        
                        // 6. Señal S O S (Código Morse ... --- ...)
                        emergencyButton(
                            title: "Señal S O S",
                            systemIcon: "bell.and.waves.left.and.right.fill",
                            bgColor: Color(red: 153/255, green: 27/255, blue: 27/255) // #991B1B
                        ) {
                            activeDescription = "Señal S O S: Código Morse internacional (... --- ...)"
                            HikerWhistlePlayer.shared.startEmergencyLoop(
                                pattern: [
                                    (200, 100), (200, 100), (200, 200),
                                    (600, 100), (600, 100), (600, 200),
                                    (200, 100), (200, 100), (200, 100)
                                ],
                                onStateChange: { isEmergencySoundLooping = $0 }
                            )
                        }
                        
                        // 7. Lanzar 911 por Beacon
                        emergencyButton(
                            title: viewModel.isEmergency911Active ? "Cancelar 911 por Beacon" : "Lanzar 911 por Beacon",
                            systemIcon: "exclamationmark.triangle.fill",
                            bgColor: Color(red: 220/255, green: 38/255, blue: 38/255) // #DC2626
                        ) {
                            let willActivate = !viewModel.isEmergency911Active
                            activeDescription = willActivate
                                ? "Lanzar 911 por Beacon: Emitiendo señal de emergencia 911 por Bluetooth"
                                : "Señal 911 por Beacon cancelada"
                            viewModel.toggleEmergency911()
                        }
                    }
                    
                    // Tarjeta explicativa dinámica (1:1 Android)
                    if let desc = activeDescription {
                        Spacer().frame(height: 16)
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("Descripción del sonido tocado:")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(AppTheme.accent)
                                
                                Spacer()
                                
                                if isEmergencySoundLooping {
                                    Button {
                                        HikerWhistlePlayer.shared.stop()
                                        isEmergencySoundLooping = false
                                    } label: {
                                        HStack(spacing: 4) {
                                            Image(systemName: "stop.fill")
                                                .font(.system(size: 9, weight: .bold))
                                            Text("Detener audio")
                                                .font(.system(size: 10, weight: .bold))
                                        }
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(AppTheme.alert)
                                        .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            
                            Text(desc)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(AppTheme.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(AppTheme.cardMuted)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(AppTheme.accent.opacity(0.4), lineWidth: 1)
                        )
                    }
                    
                    Spacer().frame(height: 18)
                    
                    // Barra Inferior: Botón de Linterna + Cerrar (1:1 Android líneas 2288-2335)
                    HStack {
                        Button {
                            toggleFlashlight()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: isFlashlightOn ? "flashlight.off.fill" : "flashlight.on.fill")
                                    .font(.system(size: 15, weight: .bold))
                                Text(isFlashlightOn ? "Apagar!!!" : "Linterna")
                                    .font(.system(size: 14, weight: .heavy))
                            }
                            .foregroundStyle(isFlashlightOn ? Color.white : Color.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                isFlashlightOn
                                    ? Color.red
                                    : Color(red: 255/255, green: 235/255, blue: 59/255) // #FFEB3B
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                        
                        Spacer()
                        
                        Button {
                            HikerWhistlePlayer.shared.stop()
                            dismiss()
                        } label: {
                            Text("Cerrar")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(AppTheme.textSecondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(20)
                .background(AppTheme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(AppTheme.accent.opacity(0.35), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.45), radius: 18, y: 8)
                .padding(.horizontal, 18)
                .padding(.vertical, 24)
            }
        }
        .presentationBackground(.clear)
        .onAppear {
            syncFlashlightState()
            isEmergencySoundLooping = HikerWhistlePlayer.shared.isRepeatingEmergency
        }
        .onDisappear {
            // Al cerrar el diálogo se detienen los pitidos activos (la linterna conserva su estado por si camina de noche)
            HikerWhistlePlayer.shared.stop()
        }
    }
    
    // MARK: - Subcomponentes de Botones 1:1 Android
    
    @ViewBuilder
    private func outlinedCommButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Spacer()
                Image(systemName: "speaker.wave.2.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(AppTheme.accent)
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(AppTheme.textPrimary)
                Spacer()
            }
            .padding(.vertical, 11)
            .background(Color.white.opacity(0.03))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(AppTheme.textSecondary.opacity(0.45), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
    
    @ViewBuilder
    private func emergencyButton(title: String, systemIcon: String, bgColor: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Spacer()
                Image(systemName: systemIcon)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
            }
            .padding(.vertical, 11)
            .background(bgColor)
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Control de Linterna (AVCaptureDevice Torch)
    
    private func syncFlashlightState() {
        #if os(iOS)
        if let device = AVCaptureDevice.default(for: .video), device.hasTorch {
            isFlashlightOn = (device.torchMode == .on)
        }
        #endif
    }
    
    private func toggleFlashlight() {
        #if os(iOS)
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else {
            activeDescription = "Este dispositivo no cuenta con linterna disponible."
            return
        }
        do {
            try device.lockForConfiguration()
            let targetOn = !isFlashlightOn
            if targetOn {
                try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
            } else {
                device.torchMode = .off
            }
            device.unlockForConfiguration()
            isFlashlightOn = targetOn
        } catch {
            activeDescription = "No se pudo accionar la linterna: \(error.localizedDescription)"
        }
        #endif
    }
}
