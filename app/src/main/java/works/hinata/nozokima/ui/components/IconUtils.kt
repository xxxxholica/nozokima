package works.hinata.nozokima.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RequestPage
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import works.hinata.nozokima.data.local.entities.CategoryEntity

private val iconMap = mapOf(
    "ShoppingCart" to Icons.Default.ShoppingCart,
    "Restaurant" to Icons.Default.Restaurant,
    "LocalMall" to Icons.Default.LocalMall,
    "Checkroom" to Icons.Default.Checkroom,
    "LocalCafe" to Icons.Default.LocalCafe,
    "DirectionsCar" to Icons.Default.DirectionsCar,
    "DirectionsBus" to Icons.Default.DirectionsBus,
    "Flight" to Icons.Default.Flight,
    "Home" to Icons.Default.Home,
    "Wifi" to Icons.Default.Wifi,
    "PhoneIphone" to Icons.Default.PhoneIphone,
    "School" to Icons.Default.School,
    "Build" to Icons.Default.Build,
    "FitnessCenter" to Icons.Default.FitnessCenter,
    "MedicalServices" to Icons.Default.MedicalServices,
    "Payments" to Icons.Default.Payments,
    "Savings" to Icons.Default.Savings,
    "CardGiftcard" to Icons.Default.CardGiftcard,
    "Celebration" to Icons.Default.Celebration,
    "TheaterComedy" to Icons.Default.TheaterComedy,
    "Movie" to Icons.Default.Movie,
    "Pets" to Icons.Default.Pets,
    "Brush" to Icons.Default.Brush,
    "Code" to Icons.Default.Code,
    "Place" to Icons.Default.Place,
    "Favorite" to Icons.Default.Favorite,
    "Star" to Icons.Default.Star,
    "Face" to Icons.Default.Face,
    "Info" to Icons.Default.Info,
    "AccountBalance" to Icons.Default.AccountBalance,
    "Refresh" to Icons.Default.Refresh,
    "History" to Icons.Default.History,
    "ShowChart" to Icons.AutoMirrored.Filled.ShowChart,
    "MoreHoriz" to Icons.Default.MoreHoriz
)

fun getCategoryIcon(categoryName: String, customCategories: List<CategoryEntity> = emptyList()): ImageVector {
    // 1. カスタムカテゴリからの検索
    customCategories.find { it.name == categoryName }?.let { cat ->
        iconMap[cat.iconName]?.let { return it }
    }
    
    // 2. 固定の対応付け（旧仕様互換）
    return when(categoryName) {
        "食生活" -> Icons.Default.Restaurant
        "住まい" -> Icons.Default.Home
        "インフラ" -> Icons.Default.Wifi
        "日用雑貨" -> Icons.Default.LocalMall
        "移動・交通" -> Icons.Default.Place
        "健康・医療" -> Icons.Default.MedicalServices
        "自分磨き" -> Icons.Default.School
        "レジャー" -> Icons.Default.Star
        "交際・贈答" -> Icons.Default.Favorite
        "美容・装い" -> Icons.Default.Face
        "特別な支出" -> Icons.Default.CardGiftcard
        "給与" -> Icons.Default.AccountBalance
        "事業・副業" -> Icons.Default.Build
        "資産運用" -> Icons.Default.Savings
        "臨時収入" -> Icons.Default.Star
        "給付・手当" -> Icons.Default.Info
        "還付・返金" -> Icons.Default.Refresh
        "贈与・祝金" -> Icons.Default.Favorite
        "ポイ活" -> Icons.Default.Payments
        "不用品売却" -> Icons.Default.LocalMall
        "繰越金" -> Icons.Default.History
        "利息・配当" -> Icons.AutoMirrored.Filled.ShowChart
        "貸付" -> Icons.Outlined.RequestPage
        "回収" -> Icons.Default.Handshake
        else -> Icons.Default.MoreHoriz
    }
}
