package works.hinata.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val appLockPassword: String? = null,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0,
    val isAssetsVisible: Boolean = true,
    val isSetupCompleted: Boolean = false,
    val themeMode: String = "SYSTEM"
)
