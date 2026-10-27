package ru.finnypet.app.ui

import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.ui.components.OwlLook

/** Окрас для тестовых контент-паков: цвета кремовой совы из pets.json. */
fun testColor(id: String = "cream") = PetColor(
    option = ContentOption(id, "pet.color.$id"),
    body = 0xFFF0D4AE,
    wing = 0xFFD9B488,
    face = 0xFFFFF5E6,
    ring = 0xFFC8996A,
)

/** Спокойная сова-детёныш для состояний экранов. */
fun testOwl(description: String = "Сова Пушок спокойна") = OwlLook(
    colors = testColor(),
    stage = GrowthStage.CUB,
    mood = PetMood.CALM,
    accessoryId = null,
    description = description,
)
