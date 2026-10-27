package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальный игровой профиль. Настоящее имя, телефон и почта не хранятся:
 * ТЗ требует работы без сбора персональных данных.
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
    /** Профиль для экспертной проверки: сбрасывается целиком. */
    val isTest: Boolean,
)
