//
//  TextToSpeechService.swift
//  RutasSendero
//
//  Motor de Síntesis de Voz (TTS) nativo de iOS con AVFoundation.
//  Equivalente 1:1 a GpsTrackingService.speakSimulationText en Android.
//  Permite la locución en español de cada punto durante la simulación / recorrido virtual.
//

import Foundation
import AVFoundation

@MainActor
public final class TextToSpeechService: NSObject, AVSpeechSynthesizerDelegate {
    public static let shared = TextToSpeechService()
    
    private let synthesizer = AVSpeechSynthesizer()
    private var onDoneCallback: (() -> Void)?
    
    public override init() {
        super.init()
        synthesizer.delegate = self
    }
    
    public var isSpeaking: Bool {
        synthesizer.isSpeaking
    }
    
    public func speak(_ text: String, onDone: (() -> Void)? = nil) {
        // Detener cualquier locución previa
        synthesizer.stopSpeaking(at: .immediate)
        self.onDoneCallback = onDone
        
        let utterance = AVSpeechUtterance(string: text)
        // Preferir voz en español de México o España
        if let voice = AVSpeechSynthesisVoice(language: "es-MX") ?? AVSpeechSynthesisVoice(language: "es-ES") {
            utterance.voice = voice
        }
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.95
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        synthesizer.speak(utterance)
    }
    
    public func stop() {
        onDoneCallback = nil
        synthesizer.stopSpeaking(at: .immediate)
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    
    public nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            let callback = self.onDoneCallback
            self.onDoneCallback = nil
            callback?()
        }
    }
    
    public nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.onDoneCallback = nil
        }
    }
}
