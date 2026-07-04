package works.hinata.nozokima.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amount: Int,
    val category: String,
    val lastUpdated: Long,
    val pointAmount: Int = 0
)
