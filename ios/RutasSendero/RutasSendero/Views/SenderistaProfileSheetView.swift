//
//  SenderistaProfileSheetView.swift
//  RutasSendero
//
//  Formulario de Registro y Perfil del Senderista en SwiftUI.
//  Incluye soporte completo para Agrupación, Sexo, Fecha de Nacimiento,
//  cálculo dinámico de Edad, Ficha Médica, Código de Confianza,
//  Auto-recuperación de datos desde la nube y sincronización PostgREST.
//

import SwiftUI

public struct SenderistaProfileSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    // MARK: - Estados del Formulario
    @State private var name: String = ""
    @State private var email: String = ""
    @State private var phone: String = ""
    @State private var role: String = "G"
    @State private var sexo: String = "M" // "M" para Masculino, "F" para Femenino
    @State private var birthDate: Date = {
        var components = DateComponents()
        components.year = 1995
        components.month = 1
        components.day = 1
        return Calendar.current.date(from: components) ?? Date()
    }()
    
    @State private var idAgrupacion: String = "MH" // Por defecto Miguel Hidalgo
    @State private var agrupaciones: [Agrupacion] = Agrupacion.defaultAgrupaciones
    @State private var codigoConfianza: String = ""
    
    // Emergencias (911)
    @State private var contactName: String = "Familia"
    @State private var emergencyPhone1: String = ""
    @State private var emergencyPhone2: String = ""
    
    // Ficha Médica y Radiofrecuencia
    @State private var bloodType: String = "O+"
    @State private var experienceLevel: String = "principiante"
    @State private var weightStr: String = "70"
    @State private var beaconId: String = ""
    @State private var hasChronicIllness: Bool = false
    @State private var chronicIllness1: String = ""
    @State private var chronicIllness2: String = ""
    @State private var hasVehicle: Bool = false
    @State private var address: String = ""
    @State private var bringsPet: Bool = false
    @State private var allergies: String = ""
    @State private var healthInsurance: String = ""
    
    // Consentimientos Legales
    @State private var acceptTerms: Bool = true
    @State private var acceptMedicalConsent: Bool = true
    
    // Auto-recuperación y Estado en Nube
    @State private var cloudRecovered: Bool = false
    @State private var isCheckingCloud: Bool = false
    @State private var cloudRecoveryNotice: String? = nil
    @State private var errorMessage: String? = nil
    
    let bloodTypes = ["O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-", "No especificado"]
    let experienceLevels = ["principiante", "intermedio", "avanzado"]
    
    // MARK: - Cálculo Dinámico de Edad
    private var calculatedAge: Int {
        Calendar.current.dateComponents([.year], from: birthDate, to: Date()).year ?? 0
    }
    
    private var formattedBirthDate: String {
        let df = DateFormatter()
        df.dateFormat = "dd/MM/yyyy"
        df.locale = Locale(identifier: "en_US_POSIX")
        return df.string(from: birthDate)
    }
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                // Cabecera Informativa
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(AppTheme.primary.opacity(0.85))
                                    .frame(width: 52, height: 52)
                                Image(systemName: "person.crop.circle.badge.checkmark")
                                    .font(.system(size: 26))
                                    .foregroundStyle(AppTheme.accent)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(viewModel.isRegistrationMandatory ? "Bienvenido a RutasSendero" : "Perfil del Senderista")
                                    .font(.headline)
                                    .foregroundStyle(AppTheme.textPrimary)
                                Text(viewModel.isRegistrationMandatory ? "Registro requerido para seguridad, 911 y balizas." : "Consulta y actualiza tu ficha de senderismo.")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                        }
                        
                        if let notice = cloudRecoveryNotice {
                            HStack(spacing: 6) {
                                Image(systemName: "icloud.and.arrow.down.fill")
                                    .foregroundStyle(AppTheme.accentCyan)
                                Text(notice)
                                    .font(.caption).bold()
                                    .foregroundStyle(AppTheme.accentCyan)
                            }
                            .padding(.vertical, 4)
                            .padding(.horizontal, 8)
                            .background(AppTheme.accentCyan.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                // 1. Datos Personales
                Section("Datos Personales") {
                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        TextField("Nombre completo *", text: $name)
                            .onChange(of: name) { _, newName in
                                updateTrustCodeIfNeeded(for: newName)
                            }
                    }
                    
                    HStack {
                        Image(systemName: "envelope.fill")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        TextField("Correo electrónico *", text: $email)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .onChange(of: email) { _, _ in
                                checkCloudRecovery()
                            }
                        
                        if isCheckingCloud {
                            ProgressView()
                                .scaleEffect(0.8)
                        }
                    }
                    
                    HStack {
                        Image(systemName: "phone.fill")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        TextField("Teléfono celular (10 dígitos) *", text: $phone)
                            .keyboardType(.phonePad)
                            .onChange(of: phone) { _, _ in
                                checkCloudRecovery()
                            }
                    }
                    
                    // Sexo Picker
                    HStack {
                        Image(systemName: "person.text.rectangle")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        Text("Sexo *")
                            .foregroundStyle(AppTheme.textPrimary)
                        Spacer()
                        Picker("Sexo", selection: $sexo) {
                            Text("Masculino").tag("M")
                            Text("Femenino").tag("F")
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 190)
                    }
                    
                    // Agrupación Senderista (MH por defecto)
                    HStack {
                        Image(systemName: "person.3.fill")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        Picker("Agrupación *", selection: $idAgrupacion) {
                            Text("Sin Agrupación (Opcional)").tag("@&")
                            ForEach(agrupaciones) { agru in
                                Text("\(agru.nombre) (\(agru.idAgrupacion))").tag(agru.idAgrupacion)
                            }
                        }
                    }
                    
                    // Fecha de Nacimiento y Cálculo Dinámico de Edad
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: "calendar")
                                .foregroundStyle(AppTheme.accent)
                                .frame(width: 24)
                            DatePicker(
                                "Fecha de Nacimiento *",
                                selection: $birthDate,
                                in: ...Date(),
                                displayedComponents: .date
                            )
                        }
                        
                        // Badge dinámico de Edad
                        HStack(spacing: 8) {
                            Spacer().frame(width: 28)
                            HStack(spacing: 4) {
                                Image(systemName: "hourglass")
                                    .font(.caption2)
                                Text("Edad calculada: \(calculatedAge) años")
                                    .font(.caption).bold()
                            }
                            .foregroundStyle(calculatedAge < 18 ? AppTheme.alert : AppTheme.accent)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background((calculatedAge < 18 ? AppTheme.alert : AppTheme.accent).opacity(0.12))
                            .clipShape(Capsule())
                            
                            if calculatedAge < 18 {
                                Text("Requiere ser mayor de 18 años")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.alert)
                            }
                        }
                    }
                    
                    // Código de Confianza
                    HStack {
                        Image(systemName: "key.fill")
                            .foregroundStyle(AppTheme.accentCyan)
                            .frame(width: 24)
                        TextField("Código de Confianza (6 caracteres)", text: $codigoConfianza)
                            .font(.system(.body, design: .monospaced))
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                        Button("Auto") {
                            generateTrustCode()
                        }
                        .buttonStyle(.bordered)
                        .font(.caption)
                    }
                    
                    // Peso Corporal
                    HStack {
                        Image(systemName: "scalemass.fill")
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 24)
                        TextField("Peso corporal en kg (para calorías)", text: $weightStr)
                            .keyboardType(.decimalPad)
                        Text("kg")
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
                
                // 2. Contactos de Emergencia (911)
                Section(header: Text("Contactos de Emergencia (Crucial para el 911)")) {
                    HStack {
                        Image(systemName: "person.2.fill")
                            .foregroundStyle(AppTheme.warningOrange)
                            .frame(width: 24)
                        TextField("A quién avisar (ej. Familiar / Pareja) *", text: $contactName)
                    }
                    
                    HStack {
                        Image(systemName: "phone.badge.waveform.fill")
                            .foregroundStyle(AppTheme.alert)
                            .frame(width: 24)
                        TextField("Teléfono de emergencia 1 *", text: $emergencyPhone1)
                            .keyboardType(.phonePad)
                    }
                    
                    HStack {
                        Image(systemName: "phone")
                            .foregroundStyle(AppTheme.textSecondary)
                            .frame(width: 24)
                        TextField("Teléfono de emergencia 2 (opcional)", text: $emergencyPhone2)
                            .keyboardType(.phonePad)
                    }
                }
                
                // 3. Ficha Médica y Radiofrecuencia
                Section("Ficha Médica y Radiofrecuencia") {
                    Picker("Tipo de Sangre", selection: $bloodType) {
                        ForEach(bloodTypes, id: \.self) { type in
                            Text(type).tag(type)
                        }
                    }
                    
                    Picker("Nivel Senderista", selection: $experienceLevel) {
                        ForEach(experienceLevels, id: \.self) { lvl in
                            Text(lvl.capitalized).tag(lvl)
                        }
                    }
                    
                    // Baliza BLE
                    HStack {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .foregroundStyle(AppTheme.accentCyan)
                            .frame(width: 24)
                        TextField("ID Baliza BLE (12 hex)", text: $beaconId)
                            .font(.system(.body, design: .monospaced))
                            .autocorrectionDisabled()
                        Button("Auto") {
                            beaconId = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
                        }
                        .buttonStyle(.bordered)
                        .font(.caption)
                    }
                    
                    // Enfermedades Crónicas
                    Toggle("¿Tiene Enfermedad Crónica?", isOn: $hasChronicIllness)
                    
                    if hasChronicIllness {
                        TextField("Enfermedad Crónica 1 * (Obligatoria)", text: $chronicIllness1)
                            .padding(.leading, 8)
                        TextField("Enfermedad Crónica 2 (Opcional)", text: $chronicIllness2)
                            .padding(.leading, 8)
                    }
                    
                    // Alergias y Seguro
                    TextField("Alergias médicas / alimentarias", text: $allergies)
                    TextField("Seguro Médico (ej. IMSS, ISSSTE, Particular)", text: $healthInsurance)
                }
                
                // 4. Estilo de Vida y Logística
                Section("Estilo de Vida y Logística en Ruta") {
                    Toggle("¿Lleva Vehículo / Auto al Sendero?", isOn: $hasVehicle)
                    Toggle("¿Lleva Mascota?", isOn: $bringsPet)
                    TextField("Dirección o Ciudad de Origen (opcional)", text: $address)
                }
                
                // 5. Consentimientos y Términos Legales
                Section("Consentimientos Legales") {
                    Toggle("Acepto Términos de Servicio y Rastreo GPS de Emergencia *", isOn: $acceptTerms)
                        .font(.footnote)
                    
                    Toggle("Consiento el tratamiento de mi Ficha Médica para rescate 911 *", isOn: $acceptMedicalConsent)
                        .font(.footnote)
                    
                    if let error = errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(AppTheme.alert)
                    }
                }
            }
            .scrollDismissesKeyboard(.immediately)
            
            // Botón Inferior Fijo
            VStack(spacing: 4) {
                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(AppTheme.alert)
                }
                
                Button {
                    validateAndSave()
                } label: {
                    HStack {
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 16, weight: .bold))
                        Text(viewModel.isRegistrationMandatory ? "COMPLETAR REGISTRO Y ENTRAR" : "GUARDAR CAMBIOS")
                            .font(.system(size: 15, weight: .bold))
                        Spacer()
                    }
                    .foregroundStyle(.white)
                    .padding(.vertical, 14)
                    .background(AppTheme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .shadow(color: AppTheme.primary.opacity(0.4), radius: 6, y: 3)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(AppTheme.cardBackground.opacity(0.95))
            .navigationTitle(viewModel.isRegistrationMandatory ? "Registro Requerido" : "Mi Perfil")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                #if targetEnvironment(simulator)
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        self.name = "Jorge Gaspar"
                        self.email = "jorge@rutassendero.com"
                        self.phone = "3121234567"
                        self.sexo = "M"
                        self.idAgrupacion = "MH"
                        self.codigoConfianza = "JG7890"
                        self.contactName = "Familia"
                        self.emergencyPhone1 = "3129876543"
                        self.weightStr = "75"
                        self.role = "G"
                        self.beaconId = "4b3c2d1e0f9a"
                        self.acceptTerms = true
                        self.acceptMedicalConsent = true
                    } label: {
                        Text("⚡️ Autocompletar")
                            .font(.caption).bold()
                            .foregroundStyle(AppTheme.accentCyan)
                    }
                }
                #endif
                
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        validateAndSave()
                    } label: {
                        Text("Entrar")
                            .font(.subheadline).bold()
                            .foregroundStyle(AppTheme.accent)
                    }
                }
                
                if !viewModel.isRegistrationMandatory {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cerrar") {
                            dismiss()
                        }
                    }
                }
            }
            .interactiveDismissDisabled(viewModel.isRegistrationMandatory)
            .onAppear {
                loadExistingProfile()
                loadAgrupaciones()
            }
        }
    }
    
    // MARK: - Cargar Agrupaciones desde la Nube
    private func loadAgrupaciones() {
        Task {
            let list = await CloudSyncService.shared.fetchAgrupaciones()
            await MainActor.run {
                self.agrupaciones = list
            }
        }
    }
    
    // MARK: - Cargar Perfil Existente
    private func loadExistingProfile() {
        if let current = viewModel.currentHikerProfile {
            self.name = current.name
            self.email = current.email
            self.phone = current.phone
            self.role = current.role.isEmpty ? "G" : current.role
            self.sexo = current.sexo.isEmpty ? "M" : current.sexo
            
            // Parsear fecha de nacimiento existente
            let df = DateFormatter()
            df.locale = Locale(identifier: "en_US_POSIX")
            for fmt in ["dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"] {
                df.dateFormat = fmt
                if let d = df.date(from: current.fechaNacimiento) {
                    self.birthDate = d
                    break
                }
            }
            
            self.idAgrupacion = current.idAgrupacion ?? "MH"
            self.codigoConfianza = current.codigoConfianza ?? ""
            self.contactName = current.contactName
            self.emergencyPhone1 = current.emergencyPhone1
            self.emergencyPhone2 = current.emergencyPhone2 ?? ""
            self.bloodType = current.bloodType ?? "O+"
            self.experienceLevel = current.experienceLevel
            self.weightStr = String(format: "%.0f", current.weightKg)
            self.beaconId = current.beaconId ?? ""
            
            self.hasChronicIllness = current.tieneEnfermedadCronica
            self.chronicIllness1 = current.enfermedadCronica1 ?? ""
            self.chronicIllness2 = current.enfermedadCronica2 ?? ""
            self.hasVehicle = current.tieneAuto
            self.address = current.direccion ?? ""
            self.bringsPet = current.llevaMascota
            self.allergies = current.alergias ?? ""
            self.healthInsurance = current.seguroMedico ?? ""
            self.acceptTerms = current.consentimientoTerminos
            self.acceptMedicalConsent = current.consentimientoFichaMedica
        } else {
            if beaconId.isEmpty {
                beaconId = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
            }
            if codigoConfianza.isEmpty {
                generateTrustCode()
            }
        }
    }
    
    // MARK: - Auto-Recuperación de la Nube
    private func checkCloudRecovery() {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPhoneDigits = phone.filter { $0.isNumber }
        
        guard viewModel.currentHikerProfile == nil,
              !cloudRecovered,
              cleanEmail.contains("@") && cleanEmail.contains("."),
              cleanPhoneDigits.count >= 10 else {
            return
        }
        
        Task {
            await MainActor.run { isCheckingCloud = true }
            do {
                if let cloudProfile = try await CloudSyncService.shared.fetchSenderistaByEmail(email: cleanEmail) {
                    let cloudPhoneDigits = cloudProfile.phone.filter { $0.isNumber }
                    let match = (cloudPhoneDigits.suffix(10) == cleanPhoneDigits.suffix(10))
                    
                    if match {
                        await MainActor.run {
                            self.cloudRecovered = true
                            self.name = cloudProfile.name
                            self.role = cloudProfile.role.uppercased() == "G" ? "G" : cloudProfile.role.uppercased()
                            self.sexo = cloudProfile.sexo
                            
                            let df = DateFormatter()
                            df.locale = Locale(identifier: "en_US_POSIX")
                            for fmt in ["dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"] {
                                df.dateFormat = fmt
                                if let d = df.date(from: cloudProfile.fechaNacimiento) {
                                    self.birthDate = d
                                    break
                                }
                            }
                            
                            self.idAgrupacion = cloudProfile.idAgrupacion ?? "MH"
                            self.codigoConfianza = cloudProfile.codigoConfianza ?? ""
                            self.contactName = cloudProfile.contactName
                            self.emergencyPhone1 = cloudProfile.emergencyPhone1
                            self.emergencyPhone2 = cloudProfile.emergencyPhone2 ?? ""
                            self.bloodType = cloudProfile.bloodType ?? "O+"
                            self.experienceLevel = cloudProfile.experienceLevel
                            self.weightStr = String(format: "%.0f", cloudProfile.weightKg)
                            self.beaconId = cloudProfile.beaconId ?? ""
                            self.hasChronicIllness = cloudProfile.tieneEnfermedadCronica
                            self.chronicIllness1 = cloudProfile.enfermedadCronica1 ?? ""
                            self.chronicIllness2 = cloudProfile.enfermedadCronica2 ?? ""
                            self.hasVehicle = cloudProfile.tieneAuto
                            self.address = cloudProfile.direccion ?? ""
                            self.bringsPet = cloudProfile.llevaMascota
                            self.allergies = cloudProfile.alergias ?? ""
                            self.healthInsurance = cloudProfile.seguroMedico ?? ""
                            self.cloudRecoveryNotice = "¡Datos recuperados de la nube para \(cleanEmail)!"
                            self.isCheckingCloud = false
                        }
                    }
                }
            } catch {
                // Silencioso si no se encuentra
            }
            await MainActor.run { isCheckingCloud = false }
        }
    }
    
    // MARK: - Generación de Código de Confianza
    private func updateTrustCodeIfNeeded(for newName: String) {
        if codigoConfianza.isEmpty && !newName.trimmingCharacters(in: .whitespaces).isEmpty {
            generateTrustCode()
        }
    }
    
    private func generateTrustCode() {
        let clean = name.trimmingCharacters(in: .whitespaces)
        let initials = clean.components(separatedBy: .whitespaces)
            .compactMap { $0.first?.uppercased() }
            .joined()
        let chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var code = initials
        while code.count < 6 {
            if let randomChar = chars.randomElement() {
                code.append(randomChar)
            }
        }
        codigoConfianza = String(code.prefix(6)).uppercased()
    }
    
    // MARK: - Validación y Guardado
    private func validateAndSave() {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmergency = emergencyPhone1.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !cleanName.isEmpty else {
            errorMessage = "Por favor ingresa tu nombre completo."
            return
        }
        guard cleanEmail.contains("@") && cleanEmail.contains(".") else {
            errorMessage = "Ingresa un correo electrónico válido."
            return
        }
        guard cleanPhone.count >= 10 else {
            errorMessage = "El teléfono celular debe tener al menos 10 dígitos."
            return
        }
        guard calculatedAge >= 18 else {
            errorMessage = "Debes ser mayor de 18 años para realizar actividades de senderismo (Edad actual: \(calculatedAge))."
            return
        }
        guard !cleanEmergency.isEmpty else {
            errorMessage = "El teléfono de emergencia es obligatorio para asistencia 911."
            return
        }
        if hasChronicIllness && chronicIllness1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errorMessage = "Por favor especifica la Enfermedad Crónica 1 obligatoria."
            return
        }
        guard codigoConfianza.count >= 3 else {
            errorMessage = "El código de confianza debe tener al menos 3 caracteres."
            return
        }
        guard acceptTerms else {
            errorMessage = "Debes aceptar los términos y condiciones de senderismo."
            return
        }
        guard acceptMedicalConsent else {
            errorMessage = "Debes autorizar el consentimiento de ficha médica para el 911."
            return
        }
        
        let weight = Double(weightStr) ?? 70.0
        let finalBeacon = beaconId.isEmpty ? String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased() : beaconId
        let finalAgrupacion = (idAgrupacion == "@&" || idAgrupacion.isEmpty) ? nil : idAgrupacion.trimmingCharacters(in: .whitespacesAndNewlines)
        
        let profile = HikerProfile(
            email: cleanEmail,
            name: cleanName,
            phone: cleanPhone,
            role: "G", // Rol de Guía con permisos completos
            sexo: sexo,
            fechaNacimiento: formattedBirthDate,
            idAgrupacion: finalAgrupacion,
            codigoConfianza: codigoConfianza.uppercased(),
            beaconId: finalBeacon,
            experienceLevel: experienceLevel,
            bloodType: bloodType,
            contactName: contactName.isEmpty ? "Familia" : contactName,
            emergencyPhone1: cleanEmergency,
            emergencyPhone2: emergencyPhone2.isEmpty ? nil : emergencyPhone2,
            weightKg: weight,
            tieneEnfermedadCronica: hasChronicIllness,
            enfermedadCronica1: hasChronicIllness ? chronicIllness1.trimmingCharacters(in: .whitespacesAndNewlines) : nil,
            enfermedadCronica2: hasChronicIllness ? chronicIllness2.trimmingCharacters(in: .whitespacesAndNewlines) : nil,
            tieneAuto: hasVehicle,
            direccion: address.isEmpty ? nil : address.trimmingCharacters(in: .whitespacesAndNewlines),
            llevaMascota: bringsPet,
            alergias: allergies.isEmpty ? nil : allergies.trimmingCharacters(in: .whitespacesAndNewlines),
            seguroMedico: healthInsurance.isEmpty ? nil : healthInsurance.trimmingCharacters(in: .whitespacesAndNewlines),
            consentimientoTerminos: acceptTerms,
            consentimientoFichaMedica: acceptMedicalConsent,
            consentimientoGpsMultimedia: acceptTerms
        )
        
        Task {
            await viewModel.saveSenderistaProfile(profile)
            dismiss()
        }
    }
}
