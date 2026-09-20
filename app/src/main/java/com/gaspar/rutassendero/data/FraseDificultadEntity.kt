package com.gaspar.rutassendero.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "frases_dificultad")
data class FraseDificultadEntity(
    @PrimaryKey
    val id: String,
    val categoria: String,
    val frase: String
) {
    companion object {
        val SEED_FRASES = listOf(
            // Principiante
            FraseDificultadEntity("p1", "Principiante", "Es fácil, hasta mi abuelita en pantuflas lo haría."),
            FraseDificultadEntity("p2", "Principiante", "Más que senderismo, esto es un paseo para ir por el pan."),
            FraseDificultadEntity("p3", "Principiante", "Nivel de dificultad: abrir la nevera."),
            FraseDificultadEntity("p4", "Principiante", "Si te cansas aquí, el sofá es tu verdadero hábitat natural."),
            FraseDificultadEntity("p5", "Principiante", "Apto para bebés, perritos cansados y personas que odian el sudor."),
            FraseDificultadEntity("p6", "Principiante", "Ruta ideal para tomarte selfies sin despeinarte ni un poquito."),
            FraseDificultadEntity("p7", "Principiante", "El único peligro aquí es que te aburras de lo plano que está."),
            FraseDificultadEntity("p8", "Principiante", "Perfecto para caminar mientras hablas por teléfono y comes papitas."),

            // Intermedio
            FraseDificultadEntity("i1", "Intermedio", "Tienes que entrenar, mi chavo, para poder terminarla."),
            FraseDificultadEntity("i2", "Intermedio", "Es hora de desempolvar las botas y fingir que tienes condición física."),
            FraseDificultadEntity("i3", "Intermedio", "Tus piernas van a empezar a cuestionar tus decisiones de vida."),
            FraseDificultadEntity("i4", "Intermedio", "Ruta de nivel medio: sudarás lo suficiente para arrepentirte a la mitad."),
            FraseDificultadEntity("i5", "Intermedio", "Ideal para los que creen que están listos, pero descubrirán que no."),
            FraseDificultadEntity("i6", "Intermedio", "Un par de subidas y recordarás por qué no vas al gimnasio."),
            FraseDificultadEntity("i7", "Intermedio", "No es el Everest, pero vas a pedirle a Dios llegar pronto."),
            FraseDificultadEntity("i8", "Intermedio", "Trae agua y dignidad, porque podrías perder ambas a medio camino."),

            // Experto
            FraseDificultadEntity("e1", "Experto", "Lleva suficiente agua y despídete de tus rodillas por una semana."),
            FraseDificultadEntity("e2", "Experto", "Si escuchas a tus rodillas crujir, es que estás en la dirección correcta."),
            FraseDificultadEntity("e3", "Experto", "Doble calcetín, triple ración de agua y mucha fuerza de voluntad."),
            FraseDificultadEntity("e4", "Experto", "Tus pantorrillas van a declararse en huelga permanente mañana."),
            FraseDificultadEntity("e5", "Experto", "Aquí es donde los niños se convierten en hombres y las rodillas en gelatina."),
            FraseDificultadEntity("e6", "Experto", "No apto para quejumbrosos ni amantes de los domingos perezosos."),
            FraseDificultadEntity("e7", "Experto", "La bajada te hará rezar por un teleférico que nunca llegará."),
            FraseDificultadEntity("e8", "Experto", "Tu cara de cansancio en la cima será épica, si es que logras llegar."),

            // Nivel Dios
            FraseDificultadEntity("d1", "Nivel Dios", "Sólo para cabritos monteses y semidioses del Olimpo. ¡Buena suerte!"),
            FraseDificultadEntity("d2", "Nivel Dios", "Trae testamento, GPS satelital y tres pulmones de repuesto."),
            FraseDificultadEntity("d3", "Nivel Dios", "Tus piernas no te van a hablar durante las próximas dos semanas."),
            FraseDificultadEntity("d4", "Nivel Dios", "Incluso los satélites de Google Maps dudan si deberías estar aquí."),
            FraseDificultadEntity("d5", "Nivel Dios", "Si logras terminar esta ruta, puedes considerarte inmortal."),
            FraseDificultadEntity("d6", "Nivel Dios", "Donde el camino termina, empieza tu calvario de senderista extremo."),
            FraseDificultadEntity("d7", "Nivel Dios", "Prepárate para ver pasar toda tu vida ante tus ojos en cada subida."),
            FraseDificultadEntity("d8", "Nivel Dios", "Los ángeles cantarán al llegar al final... o tal vez sea tu alucinación por fatiga.")
        )
    }
}
