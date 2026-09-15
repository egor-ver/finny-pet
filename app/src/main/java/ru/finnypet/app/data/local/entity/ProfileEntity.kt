package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальный игровой профиль. Настоящее имя, телефон и почта не хранятся:
 * ТЗ 3.5 требует работы без сбора персональных данных.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val childName: String,
    val petName: String,
    val bodyId: String,
    val colorId: String,
    val accessoryId: String?,
    val createdAt: Long,
    /** Профиль для экспертной проверки: сбрасывается целиком (ТЗ 2.5.13). */
    val isTest: Boolean,
)
