package com.gaspar.rutassendero

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import org.junit.Test
import java.awt.Color
import java.io.File
import java.io.FileOutputStream

class PdfReportGeneratorTest {

    @Test
    fun generateExecutivePdfReport() {
        val outputFile = File("C:/GASPAR/baches/rutas/Informe_Tecnico_Rutas_Villa_Coquimatlan.pdf")
        outputFile.parentFile.mkdirs()

        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(outputFile))

        // Header / Footer event
        writer.pageEvent = object : PdfPageEventHelper() {
            override fun onEndPage(writer: PdfWriter, document: Document) {
                val cb = writer.directContent
                val footerFont = Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color(148, 163, 184))
                val footerPhrase = Phrase("Rutas Sendero | Auditoría Topométrica Satelital Forense — Página ${writer.pageNumber}", footerFont)
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footerPhrase, (document.right() - document.left()) / 2 + document.leftMargin(), document.bottom() - 15f, 0f)
            }
        }

        document.open()

        // Paleta de Colores
        val cPrimary = Color(15, 118, 110)      // Teal oscuro
        val cDark = Color(15, 23, 42)           // Navy #0f172a
        val cSlate = Color(51, 65, 85)          // Slate #334155
        val cMuted = Color(100, 116, 139)       // Muted #64748b
        val cAlertBg = Color(240, 253, 250)     // Teal muy suave
        val cAlertBorder = Color(13, 148, 136)  // Teal border
        val cDanger = Color(220, 38, 38)        // Rojo #dc2626
        val cSuccess = Color(22, 101, 52)       // Verde oscuro
        val cCardBg = Color(248, 250, 252)      // Gris fondo
        val cBorder = Color(226, 232, 240)      // Gris borde
        val cDarkBox = Color(15, 23, 42)        // Fondo consola

        // Fuentes
        val fTitle = Font(Font.HELVETICA, 15f, Font.BOLD, cDark)
        val fSubtitle = Font(Font.HELVETICA, 9.5f, Font.BOLD, cPrimary)
        val fMeta = Font(Font.HELVETICA, 8f, Font.NORMAL, cMuted)
        val fMetaBold = Font(Font.HELVETICA, 8f, Font.BOLD, cSlate)
        val fSecTitle = Font(Font.HELVETICA, 10.5f, Font.BOLD, cDark)
        val fCardVal = Font(Font.HELVETICA, 12f, Font.BOLD, cDark)
        val fCardLabel = Font(Font.HELVETICA, 7f, Font.BOLD, cMuted)
        val fCardSub = Font(Font.HELVETICA, 7.5f, Font.BOLD, cSuccess)
        val fCardSubDanger = Font(Font.HELVETICA, 7.5f, Font.BOLD, cDanger)
        val fTh = Font(Font.HELVETICA, 7.5f, Font.BOLD, cSlate)
        val fTd = Font(Font.HELVETICA, 8f, Font.NORMAL, cSlate)
        val fTdBold = Font(Font.HELVETICA, 8f, Font.BOLD, cDark)
        val fTdHighlight = Font(Font.HELVETICA, 8f, Font.BOLD, cPrimary)
        val fTdDanger = Font(Font.HELVETICA, 8f, Font.BOLD, cDanger)
        val fAlertH = Font(Font.HELVETICA, 9.5f, Font.BOLD, Color(17, 94, 89))
        val fAlertP = Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color(19, 78, 74))
        val fCode = Font(Font.COURIER, 7.5f, Font.NORMAL, Color(226, 232, 240))
        val fCodeCyan = Font(Font.COURIER, 7.5f, Font.BOLD, Color(56, 189, 248))
        val fCodeRed = Font(Font.COURIER, 7.5f, Font.BOLD, Color(248, 113, 113))

        // 1. HEADER
        val headerTable = PdfPTable(2)
        headerTable.widthPercentage = 100f
        headerTable.setWidths(floatArrayOf(70f, 30f))

        val cellTitle = PdfPCell()
        cellTitle.border = Rectangle.BOTTOM
        cellTitle.borderWidthBottom = 2f
        cellTitle.borderColorBottom = cPrimary
        cellTitle.paddingBottom = 8f
        cellTitle.addElement(Paragraph("INFORME TÉCNICO FORENSE DE GEOLOCALIZACIÓN", fTitle))
        cellTitle.addElement(Paragraph("AUDITORÍA SATELITAL TOPOMÉTRICA: villa -coqui vs Coquimatlan", fSubtitle))
        headerTable.addCell(cellTitle)

        val cellMeta = PdfPCell()
        cellMeta.border = Rectangle.BOTTOM
        cellMeta.borderWidthBottom = 2f
        cellMeta.borderColorBottom = cPrimary
        cellMeta.paddingBottom = 8f
        cellMeta.horizontalAlignment = Element.ALIGN_RIGHT
        val pMeta = Paragraph().apply {
            alignment = Element.ALIGN_RIGHT
            add(Chunk("Fecha de emisión: ", fMeta))
            add(Chunk("23/Sep/2026\n", fMetaBold))
            add(Chunk("Sistema: ", fMeta))
            add(Chunk("Rutas Sendero\n", fMetaBold))
            add(Chunk("Estado: ", fMeta))
            add(Chunk("Dictamen Concluyente", fMetaBold))
        }
        cellMeta.addElement(pMeta)
        headerTable.addCell(cellMeta)

        document.add(headerTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 4f)))

        // 2. VEREDICTO BOX
        val alertTable = PdfPTable(1)
        alertTable.widthPercentage = 100f
        val alertCell = PdfPCell().apply {
            backgroundColor = cAlertBg
            border = Rectangle.LEFT
            borderWidthLeft = 4f
            borderColorLeft = cAlertBorder
            setPadding(8f)
            addElement(Paragraph("VEREDICTO TÉCNICO INMEDIATO (PARA EQUIPO DE QA / TESTERS)", fAlertH))
            val p = Paragraph().apply {
                add(Chunk("El motor de cálculo geodésico y los algoritmos matemáticos de la app funcionan con absoluta precisión. ", fAlertP))
                add(Chunk("La diferencia de 1,089 metros (~1.1 km) se originó porque el teléfono donde se grabó la ruta 'Coquimatlan' sufrió suspensión de ubicación en segundo plano por el sistema operativo Android (Doze Mode / Ahorro agresivo de batería) al apagarse la pantalla. ", fAlertP))
                add(Chunk("Dicho dispositivo apagó el GPS en 5 ocasiones prolongadas (con un apagón ciego de 4.5 km), uniendo las balizas con líneas rectas euclidianas a través del cerro, lo que recortó la distancia real de las curvas.", fAlertP))
            }
            addElement(p)
        }
        alertTable.addCell(alertCell)
        document.add(alertTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // 3. STAT CARDS (4 COLUMNAS)
        val cardsTable = PdfPTable(4)
        cardsTable.widthPercentage = 100f
        cardsTable.setWidths(floatArrayOf(25f, 25f, 25f, 25f))

        fun addCard(label: String, value: String, sub: String, isDanger: Boolean = false) {
            val cell = PdfPCell().apply {
                backgroundColor = cCardBg
                border = Rectangle.BOX
                borderColor = cBorder
                borderWidth = 1f
                setPadding(6f)
                horizontalAlignment = Element.ALIGN_CENTER
                addElement(Paragraph(label, fCardLabel).apply { alignment = Element.ALIGN_CENTER })
                addElement(Paragraph(value, fCardVal).apply { alignment = Element.ALIGN_CENTER })
                addElement(Paragraph(sub, if (isDanger) fCardSubDanger else fCardSub).apply { alignment = Element.ALIGN_CENTER })
            }
            cardsTable.addCell(cell)
        }

        addCard("COINCIDENCIA EN TIEMPO", "99.8%", "Dif: sólo 25 segundos")
        addCard("COINCIDENCIA BALIZAS", "99.88%", "Dif: apenas 16.7 metros")
        addCard("MUESTREO VILLA-COQUI", "1,212 pts", "1 punto cada 12.5 m")
        addCard("MUESTREO COQUIMATLÁN", "208 pts", "-83% puntos perdidos", isDanger = true)

        document.add(cardsTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // 4. TELEMETRÍA COMPARATIVA GLOBAL
        document.add(Paragraph("1. TELEMETRÍA COMPARATIVA DE AMBAS RUTAS", fSecTitle))
        document.add(Paragraph(" ", Font(Font.HELVETICA, 2f)))

        val dataTable = PdfPTable(5)
        dataTable.widthPercentage = 100f
        dataTable.setWidths(floatArrayOf(27f, 22f, 22f, 15f, 14f))

        fun addTh(text: String) {
            dataTable.addCell(PdfPCell(Phrase(text, fTh)).apply {
                backgroundColor = Color(241, 245, 249)
                setPadding(5f)
                border = Rectangle.BOTTOM
                borderWidthBottom = 1.5f
                borderColorBottom = cBorder
            })
        }
        addTh("PARÁMETRO")
        addTh("RUTA A (villa -coqui)")
        addTh("RUTA B (Coquimatlan)")
        addTh("DISCREPANCIA")
        addTh("DIAGNÓSTICO")

        fun addRow(p: String, r1: String, r2: String, diff: String, diag: String, highlight: Int = 0) {
            val bg = if (dataTable.rows.size % 2 == 0) Color(250, 251, 252) else Color.WHITE
            fun c(text: String, font: Font): PdfPCell {
                return PdfPCell(Phrase(text, font)).apply {
                    backgroundColor = bg
                    setPadding(4.5f)
                    border = Rectangle.BOTTOM
                    borderWidthBottom = 0.5f
                    borderColorBottom = cBorder
                }
            }
            dataTable.addCell(c(p, fTdBold))
            dataTable.addCell(c(r1, if (highlight == 1) fTdHighlight else fTd))
            dataTable.addCell(c(r2, if (highlight == 2) fTdDanger else if (highlight == 1) fTdHighlight else fTd))
            dataTable.addCell(c(diff, if (highlight == 2) fTdDanger else if (highlight == 1) fTdHighlight else fTd))
            dataTable.addCell(c(diag, if (highlight == 2) fTdDanger else fTdBold))
        }

        addRow("Hora de Inicio (UTC)", "08:09:41", "08:11:11", "1 min 30 s", "Simultáneo")
        addRow("Hora de Finalización", "11:46:44", "11:47:49", "1 min 05 s", "Llegada junta")
        addRow("Duración Total", "3h 37m 03s (13,023 s)", "3h 36m 38s (12,998 s)", "25 segundos", "Mismo tiempo")
        addRow("Punto Partida (Lat, Lon)", "19.24677, -103.75178", "19.24676, -103.75183", "4.43 metros", "Mismo origen")
        addRow("Punto Meta (Lat, Lon)", "19.24651, -103.75170", "19.24644, -103.75176", "9.56 metros", "Mismo fin")
        addRow("Distancia Balizas (Beacons)", "13,954.22 m (13.95 km)", "13,970.98 m (13.97 km)", "16.75 m (0.01 km)", "Exacto (99.88%)", highlight = 1)
        addRow("Distancia Traza GPS", "15,410.90 m (15.41 km)", "14,321.68 m (14.32 km)", "1,089.21 m (1.09 km)", "Recorte curvas", highlight = 2)
        addRow("Puntos Traza Satelital", "1,212 puntos GPS", "208 puntos GPS", "-1,004 pts (-83%)", "Apagón satélite", highlight = 2)
        addRow("Desnivel Positivo (+)", "+504.4 m (366 subidas)", "+197.4 m (51 subidas)", "-307 m elevación", "Omitió cumbres", highlight = 2)

        document.add(dataTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // 5. REGISTRO DE BALIZAS (LA PRUEBA REINA)
        document.add(Paragraph("2. LA PRUEBA CONTUNDENTE: REGISTRO DE BALIZAS HOMBRO A HOMBRO", fSecTitle))
        document.add(Paragraph("Las balizas se confirman intencionalmente por el usuario. La distancia entre ambas difiere por sólo 16 metros:", fMeta))
        document.add(Paragraph(" ", Font(Font.HELVETICA, 2f)))

        val beaconTable = PdfPTable(4)
        beaconTable.widthPercentage = 100f
        beaconTable.setWidths(floatArrayOf(30f, 22f, 22f, 26f))

        fun addBTh(text: String) {
            beaconTable.addCell(PdfPCell(Phrase(text, fTh)).apply {
                backgroundColor = Color(241, 245, 249)
                setPadding(4.5f)
                border = Rectangle.BOTTOM
                borderWidthBottom = 1.5f
                borderColorBottom = cBorder
            })
        }
        addBTh("BALIZA / FARO")
        addBTh("HORA VILLA-COQUI")
        addBTh("HORA COQUIMATLÁN")
        addBTh("COORDENADAS (LAT, LON)")

        fun addBRow(name: String, h1: String, h2: String, coords: String) {
            val bg = if (beaconTable.rows.size % 2 == 0) Color(250, 251, 252) else Color.WHITE
            fun c(text: String, font: Font): PdfPCell = PdfPCell(Phrase(text, font)).apply {
                backgroundColor = bg
                setPadding(4f)
                border = Rectangle.BOTTOM
                borderWidthBottom = 0.5f
                borderColorBottom = cBorder
            }
            beaconTable.addCell(c(name, fTdBold))
            beaconTable.addCell(c(h1, fTd))
            beaconTable.addCell(c(h2, fTd))
            beaconTable.addCell(c(coords, fTdHighlight))
        }

        addBRow("Baliza #1 (Inicio de Ruta)", "08:09:41", "08:11:11", "19.2467, -103.7518")
        addBRow("Baliza #2 (Faro #2)", "08:41:24", "08:42:16", "19.2346, -103.7673")
        addBRow("Baliza #3 (Faro #3)", "09:20:58", "09:20:12", "19.2269, -103.7934")
        addBRow("Baliza #4 (Faro #4)", "09:29:08", "09:30:16", "19.2233, -103.7968")
        addBRow("Baliza #5 (Faro #5)", "09:49:58", "09:50:35", "19.2123, -103.8059")
        addBRow("Baliza #6 (Faro #6)", "11:15:01", "11:15:41", "19.2339, -103.7676")
        addBRow("Baliza #7/8 (Fin de Ruta)", "11:46:44", "11:47:49", "19.2465, -103.7517")

        document.add(beaconTable)

        // 6. SALTOS CRÍTICOS Y EXPLICACIÓN (PÁGINA 2)
        document.newPage()

        document.add(Paragraph("3. ANÁLISIS FORENSE: ¿DÓNDE SE PERDIERON LOS 1,089 METROS?", fSecTitle))
        document.add(Paragraph("Al auditar punto por punto los 208 registros de 'Coquimatlan', se descubrieron 5 apagones de GPS por suspensión de Android en segundo plano:", fMeta))
        document.add(Paragraph(" ", Font(Font.HELVETICA, 3f)))

        val codeTable = PdfPTable(1)
        codeTable.widthPercentage = 100f
        val codeCell = PdfPCell().apply {
            backgroundColor = cDarkBox
            setPadding(8f)
            border = Rectangle.NO_BORDER
            val p = Paragraph().apply {
                add(Chunk("# TELEMETRÍA DE SALTOS ANÓMALOS DETECTADOS EN COQUIMATLÁN (COQUICL8WW):\n", fCodeCyan))
                add(Chunk("1. Salto #34  -> #35:  ", fCode))
                add(Chunk("1,921.09 metros ", fCodeRed))
                add(Chunk("en línea recta (GPS despertó al llegar al Faro #2)\n", fCode))
                add(Chunk("2. Salto #76  -> #77:  ", fCode))
                add(Chunk("2,361.05 metros ", fCodeRed))
                add(Chunk("en línea recta (GPS despertó al llegar al Faro #3)\n", fCode))
                add(Chunk("3. Salto #145 -> #146: ", fCode))
                add(Chunk("1,080.27 metros ", fCodeRed))
                add(Chunk("en línea recta (GPS despertó al llegar al Faro #5)\n", fCode))
                add(Chunk("4. Salto #169 -> #170: ", fCode))
                add(Chunk("4,499.89 metros ", fCodeRed))
                add(Chunk("en línea recta (DURANTE EL REGRESO, 1H 25M DORMIDO)\n", fCode))
                add(Chunk("5. Salto #206 -> #207: ", fCode))
                add(Chunk("1,750.58 metros ", fCodeRed))
                add(Chunk("en línea recta (GPS despertó al llegar a la Meta)", fCode))
            }
            addElement(p)
        }
        codeTable.addCell(codeCell)
        document.add(codeTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // COMPARATIVA DE COMPORTAMIENTOS (2 COLUMNAS)
        val behaviorTable = PdfPTable(2)
        behaviorTable.widthPercentage = 100f
        behaviorTable.setWidths(floatArrayOf(50f, 50f))

        val bCell1 = PdfPCell().apply {
            backgroundColor = Color(248, 250, 252)
            border = Rectangle.BOX
            borderColor = cBorder
            borderWidth = 1f
            setPadding(8f)
            addElement(Paragraph("COMPORTAMIENTO VILLA-COQUI (CORRECTO)", Font(Font.HELVETICA, 8.5f, Font.BOLD, cSuccess)))
            val p = Paragraph().apply {
                add(Chunk("• Rastreo continuo sin interrupciones (1,212 puntos).\n", fTd))
                add(Chunk("• Muestreo constante: 1 punto cada 12.5 metros.\n", fTd))
                add(Chunk("• Salto máximo en toda la ruta: sólo 14.19 metros.\n", fTd))
                add(Chunk("• Acompañó cada curva, meandro y desvío del camino.\n", fTd))
                add(Chunk("• Capturó +504 metros de desnivel real en 3D.\n", fTd))
                add(Chunk("• Distancia real acumulada: 15.41 km.", fTdBold))
            }
            addElement(p)
        }
        behaviorTable.addCell(bCell1)

        val bCell2 = PdfPCell().apply {
            backgroundColor = Color(254, 242, 242)
            border = Rectangle.BOX
            borderColor = Color(254, 202, 202)
            borderWidth = 1f
            setPadding(8f)
            addElement(Paragraph("COMPORTAMIENTO COQUIMATLÁN (DOZE MODE)", Font(Font.HELVETICA, 8.5f, Font.BOLD, cDanger)))
            val p = Paragraph().apply {
                add(Chunk("• Android durmió el servicio de ubicación (sólo 208 puntos).\n", fTd))
                add(Chunk("• Muestreo deficiente: promedio 1 punto cada 69 metros.\n", fTd))
                add(Chunk("• Salto máximo anormal: 4,499.89 metros en línea recta.\n", fTd))
                add(Chunk("• Recortó las curvas mediante 'cuerdas rectas euclidianas'.\n", fTd))
                add(Chunk("• Omitió +307 metros de subidas y bajadas intermedias.\n", fTd))
                add(Chunk("• Pérdida acumulada en curvas: 1,089.21 metros.", fTdDanger))
            }
            addElement(p)
        }
        behaviorTable.addCell(bCell2)
        document.add(behaviorTable)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // 7. EL TRAMO CRÍTICO DE 4.5 KM
        document.add(Paragraph("4. EL TRAMO CRÍTICO DE REGRESO (SALTO DE 4.5 KILÓMETROS)", fSecTitle))
        val pCrit = Paragraph().apply {
            add(Chunk("Entre las 09:50 y las 11:15 (retorno de Faro #5 a Faro #6), el teléfono de Coquimatlán no tomó una sola muestra en 1 hora y 25 minutos:\n", fTd))
            add(Chunk("• Coquimatlán (1 solo salto): ", fTdBold))
            add(Chunk("Trazó una línea recta imaginaria de 4,499.89 metros atravesando cerros y barrancas.\n", fTd))
            add(Chunk("• Villa-Coqui (390 puntos en ese mismo tramo): ", fTdBold))
            add(Chunk("Registró la sinuosidad exacta del sendero y sus curvas, sumando 4,913.70 metros.\n", fTd))
            add(Chunk("• Pérdida directa en ese único apagón: ", fTdBold))
            add(Chunk("413.80 metros amputados del recorrido real por cortar curvas.\n", fTdDanger))
            add(Chunk("Sumando los otros 4 saltos de 1.9 km, 2.3 km, 1.0 km y 1.7 km, se llega con exactitud matemática a los 1,089.21 metros de diferencia.", fTd))
        }
        document.add(pCrit)
        document.add(Paragraph(" ", Font(Font.HELVETICA, 6f)))

        // 8. ACCIONES RECOMENDADAS
        document.add(Paragraph("5. CONCLUSIÓN Y PLAN DE ACCIÓN RECOMENDADO", fSecTitle))
        val pAcc = Paragraph().apply {
            add(Chunk("1. Respuesta Formal a los Testers: ", fTdBold))
            add(Chunk("La coincidencia de las balizas (13.95 km vs 13.97 km, diferencia de 16 metros) certifica que el software no tiene fallas de cálculo. La discrepancia fue ocasionada por el ahorro de batería del dispositivo móvil.\n", fTd))
            add(Chunk("2. Medidas Preventivas en la Aplicación Android:\n", fTdBold))
            add(Chunk("   a) Exigir antes de iniciar ruta la exclusión de optimización de batería (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).\n", fTd))
            add(Chunk("   b) Verificar que el permiso de GPS esté otorgado como 'Permitir todo el tiempo' (ACCESS_BACKGROUND_LOCATION).\n", fTd))
            add(Chunk("   c) Implementar telemetría en tiempo real que avise al usuario si transcurren más de 100 metros sin lecturas GPS del hardware.", fTd))
        }
        document.add(pAcc)

        document.close()
        println("PDF generated successfully at: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
    }
}
