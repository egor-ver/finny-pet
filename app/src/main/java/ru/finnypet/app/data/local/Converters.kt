package ru.finnypet.app.data.local

import androidx.room.TypeConverter
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.TransactionType

/**
 * Перечисления хранятся по имени, а не по порядковому номеру.
 *
 * У [GrowthStage] порядок констант несёт смысл — GrowthEngine сравнивает стадии
 * через maxOf, — и при хранении ordinal перестановка констант молча испортила бы
 * уже сохранённые профили.
 */
class Converters {

    @TypeConverter
    fun growthStageToString(stage: GrowthStage): String = stage.name

    @TypeConverter
    fun growthStageFromString(value: String): GrowthStage = GrowthStage.valueOf(value)

    @TypeConverter
    fun periodStatusToString(status: PeriodStatus): String = status.name

    @TypeConverter
    fun periodStatusFromString(value: String): PeriodStatus = PeriodStatus.valueOf(value)

    @TypeConverter
    fun transactionTypeToString(type: TransactionType): String = type.name

    @TypeConverter
    fun transactionTypeFromString(value: String): TransactionType = TransactionType.valueOf(value)
}
