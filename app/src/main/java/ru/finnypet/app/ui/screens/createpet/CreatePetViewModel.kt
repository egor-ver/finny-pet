package ru.finnypet.app.ui.screens.createpet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.components.OwlLook
import javax.inject.Inject

/** Вариант внешности с уже подставленным названием из контент-пака. */
data class AppearanceOption(
    val id: String,
    val title: String,
)

/**
 * Что показывает экран создания питомца.
 *
 * Варианты внешности приходят из контент-пака: добавление окраса — правка
 * файла, а не кода (ТЗ 2.5.14).
 *
 * [created] — не команда, а факт: профиль заведён. Переход делает экран,
 * увидев этот флаг. Дёргать навигацию прямо из ViewModel нельзя — корутина
 * переживает поворот, а NavController нет, и переход потерялся бы.
 */
data class CreatePetState(
    /** Окрасы с цветами: сова перерисовывается сразу при выборе. */
    val palette: List<PetColor>,
    val bodies: List<AppearanceOption>,
    val colors: List<AppearanceOption>,
    val accessories: List<AppearanceOption>,
    val bodyId: String,
    val colorId: String,
    val accessoryId: String? = null,
    val childName: String = "",
    val petName: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val created: Boolean = false,
) {

    val appearance: PetAppearance
        get() = PetAppearance(bodyId = bodyId, colorId = colorId, accessoryId = accessoryId)

    /** Новая сова — детёныш и спокойна: грустить ей пока не из-за чего. */
    fun owl(description: String): OwlLook = OwlLook(
        colors = palette.first { it.id == colorId },
        stage = GrowthStage.CUB,
        mood = PetMood.CALM,
        accessoryId = accessoryId,
        description = description,
    )

    val canCreate: Boolean
        get() = childName.isNotBlank() && petName.isNotBlank() && !saving
}

@HiltViewModel
class CreatePetViewModel @Inject constructor(
    content: ContentRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(content))
    val state: StateFlow<CreatePetState> = _state.asStateFlow()

    fun selectBody(id: String) = _state.update { it.copy(bodyId = id) }

    fun selectColor(id: String) = _state.update { it.copy(colorId = id) }

    /** Пусто означает «без аксессуара» — это допустимый вариант внешности. */
    fun selectAccessory(id: String?) = _state.update { it.copy(accessoryId = id) }

    fun changeChildName(value: String) =
        _state.update { it.copy(childName = value.take(Profile.MAX_NAME_LENGTH)) }

    fun changePetName(value: String) =
        _state.update { it.copy(petName = value.take(Profile.MAX_NAME_LENGTH)) }

    /**
     * Заводит профиль и питомца. Ни настоящего имени, ни телефона, ни почты
     * не спрашиваем — ТЗ 3.5 требует работы без сбора персональных данных.
     *
     * Сбой записи не запирает экран: флаг сохранения снимается, показывается
     * объяснение, кнопка снова доступна. ТЗ 3.4 запрещает тупиковые экраны,
     * а ТЗ 2.5.9 требует объяснения на любой исход.
     */
    fun create() {
        if (!_state.value.canCreate) return

        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val current = _state.value
            runCatching {
                profiles.create(
                    childName = current.childName.trim(),
                    petName = current.petName.trim(),
                    appearance = current.appearance,
                )
            }.fold(
                onSuccess = { _state.update { it.copy(saving = false, created = true) } },
                onFailure = { _state.update { it.copy(saving = false, failed = true) } },
            )
        }
    }

    private companion object {

        fun initialState(content: ContentRepository): CreatePetState {
            val pack = content.pack()
            val pets = pack.pets

            fun options(source: List<ContentOption>) = source.map { option ->
                // Если текста нет, показываем идентификатор: пустая кнопка
                // хуже некрасивой, ребёнку нужно на что-то нажать.
                AppearanceOption(id = option.id, title = pack.texts[option.titleKey] ?: option.id)
            }

            // Непустоту списков гарантирует разбор контент-пака: файл без
            // тела или окраса не загрузится вовсе.
            return CreatePetState(
                palette = pets.colors,
                bodies = options(pets.bodies),
                colors = options(pets.colors.map { it.option }),
                accessories = options(pets.accessories),
                bodyId = pets.bodies.first().id,
                colorId = pets.colors.first().id,
            )
        }
    }
}
