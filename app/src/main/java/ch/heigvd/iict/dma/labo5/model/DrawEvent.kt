package ch.heigvd.iict.dma.labo5.model

/**
 * Évènement de dessin diffusé en temps réel entre les pairs (tableau collaboratif).
 *
 * Les coordonnées x/y sont NORMALISÉES dans [0f, 1f] : on divise par la largeur/hauteur
 * du canvas avant d'envoyer, et on remultiplie à la réception. Ça permet à deux appareils
 * d'écrans/résolutions différents de partager le même dessin sans décalage.
 */
enum class DrawAction { BEGIN, POINT, END, CLEAR }

data class DrawEvent(
    val sender: String,             // endpointId de l'auteur (rempli à la réception)
    val action: DrawAction,
    val x: Float = 0f,              // normalisé [0,1]
    val y: Float = 0f,              // normalisé [0,1]
    val color: Int = 0xFF000000.toInt(),
    val width: Float = 8f
)
