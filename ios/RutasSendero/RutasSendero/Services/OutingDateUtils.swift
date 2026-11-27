//
//  OutingDateUtils.swift
//  RutasSendero
//
//  Utilidad para parseo y comparación de fechas de salidas y eventos (1:1 Android).
//  Soporta formatos comunes de Postgres/PostgREST y fechas ISO.
//

import Foundation

public struct OutingDateUtils {
    
    /// Evalúa si la fecha es estrictamente presente o futura (con margen de gracia en horas).
    /// Si la fecha no se puede parsear o está vacía, retorna true para no bloquear preventivamente.
    public static func isFutureOrCurrent(_ dateStr: String, graceHours: Double = 6.0) -> Bool {
        guard let date = parseDate(dateStr) else { return true }
        let cutoff = Date().addingTimeInterval(-graceHours * 3600)
        return date >= cutoff
    }
    
    /// Parsea strings de fecha en formatos comunes de SQL / ISO 8601
    public static func parseDate(_ dateStr: String) -> Date? {
        let clean = dateStr.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return nil }
        
        let formats = [
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd"
        ]
        
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone.current
        
        for format in formats {
            df.dateFormat = format
            if let date = df.date(from: clean) {
                return date
            }
        }
        return nil
    }
    
    /// Formatea una fecha para mostrarla amigablemente al usuario en español
    public static func formatDisplay(_ dateStr: String) -> String {
        guard let date = parseDate(dateStr) else { return dateStr }
        let df = DateFormatter()
        df.locale = Locale(identifier: "es_MX")
        df.dateFormat = "d 'de' MMMM, yyyy HH:mm"
        return df.string(from: date)
    }
    
    /// Formato corto de fecha
    public static func formatShort(_ dateStr: String) -> String {
        guard let date = parseDate(dateStr) else { return dateStr }
        let df = DateFormatter()
        df.locale = Locale(identifier: "es_MX")
        df.dateFormat = "dd/MM/yyyy HH:mm"
        return df.string(from: date)
    }
}

public extension String {
    /// Comparación insensible a mayúsculas y minúsculas (1:1 Android equalsIgnoreCase)
    func equalsIgnoreCase(_ other: String) -> Bool {
        return self.caseInsensitiveCompare(other) == .orderedSame
    }
}
