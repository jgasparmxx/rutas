//
//  VoiceDictationService.swift
//  RutasSendero
//
//  Servicio nativo de reconocimiento de voz para dictado de descripciones
//  de puntos y notas de campo sin necesidad de teclear.
//

import Foundation
import Speech
import AVFoundation

@Observable
public final class VoiceDictationService {
    public static let shared = VoiceDictationService()
    
    public var isRecording: Bool = false
    public var transcribedText: String = ""
    public var errorMessage: String? = nil
    
    private let speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    
    public init() {
        // Priorizar español (México / España / Sistema)
        let locale = Locale(identifier: "es-MX")
        if let rec = SFSpeechRecognizer(locale: locale), rec.isAvailable {
            self.speechRecognizer = rec
        } else if let rec = SFSpeechRecognizer(locale: Locale(identifier: "es-ES")), rec.isAvailable {
            self.speechRecognizer = rec
        } else {
            self.speechRecognizer = SFSpeechRecognizer()
        }
    }
    
    public func requestPermissions() async -> Bool {
        let speechStatus = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        
        guard speechStatus else { return false }
        
        let micStatus = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        
        return micStatus
    }
    
    @MainActor
    public func startDictation(onUpdate: @escaping (String) -> Void) {
        guard !isRecording else {
            stopDictation()
            return
        }
        
        errorMessage = nil
        transcribedText = ""
        
        // Cancelar tareas previas
        recognitionTask?.cancel()
        recognitionTask = nil
        
        guard let speechRecognizer = speechRecognizer, speechRecognizer.isAvailable else {
            errorMessage = "Reconocimiento de voz no disponible en este dispositivo."
            return
        }
        
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            request.taskHint = .dictation
            self.recognitionRequest = request
            
            let inputNode = audioEngine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)
            
            // Verificar formato de audio válido (previene crash en simulador)
            guard recordingFormat.sampleRate > 0 else {
                errorMessage = "Entrada de audio no disponible en el simulador."
                return
            }
            
            inputNode.removeTap(onBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
                request.append(buffer)
            }
            
            audioEngine.prepare()
            try audioEngine.start()
            
            isRecording = true
            
            recognitionTask = speechRecognizer.recognitionTask(with: request) { [weak self] result, error in
                guard let self = self else { return }
                
                if let result = result {
                    let text = result.bestTranscription.formattedString
                    Task { @MainActor in
                        self.transcribedText = text
                        onUpdate(text)
                    }
                }
                
                if error != nil || (result?.isFinal ?? false) {
                    Task { @MainActor in
                        self.stopDictation()
                    }
                }
            }
        } catch {
            errorMessage = "Error al iniciar micrófono: \(error.localizedDescription)"
            stopDictation()
        }
    }
    
    @MainActor
    public func stopDictation() {
        guard isRecording else { return }
        
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        recognitionRequest = nil
        recognitionTask = nil
        isRecording = false
        
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
