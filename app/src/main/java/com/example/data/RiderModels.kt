package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rider_profile")
data class RiderProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "卡尔克（张晓明）",
    val riderId: String = "GH-88291",
    val rating: Float = 4.98f,
    val isOnline: Boolean = true,
    val onlineStatusText: String = "在线接单", // "在线接单", "暂时下线(15分钟)", "暂时下线(30分钟)", "休息/离线"
    val selectedTheme: String = "HUSTLE", // HUSTLE, MEITUAN, ELEME, JD
    val totalBalance: Double = 1240.80,
    val todayEarnings: Double = 186.50,
    val withdrawableBalance: Double = 650.00,
    val completedOrdersCount: Int = 182,
    val completionRate: Float = 99.8f,
    val email: String = "clarkejustin42be@gmail.com",
    val password: String = "rider123456",
    val phone: String = "13888887321",
    val avatarUri: String? = null
)

@Entity(tableName = "delivery_orders")
data class DeliveryOrder(
    @PrimaryKey val id: String, // e.g. "#88219"
    val customerName: String,
    val phone: String,
    val pickupAddress: String,
    val pickupDistance: Double, // km
    val deliveryAddress: String,
    val deliveryDistance: Double, // km
    val estTimeMinutes: Int,
    val itemCount: Int,
    val price: Double,
    val subsidy: Double,
    val remark: String,
    val status: String, // "PENDING_GRAB", "DELIVERING", "COMPLETED"
    val specialTag: String? = null, // e.g., "超重货物(>10kg)", "高额奖赏", "即时即送"
    val acceptTime: Long? = null,
    val completeTime: Long? = null
)

@Entity(tableName = "wallet_transactions")
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderId: String? = null,
    val amount: Double,
    val type: String, // "DELIVERY_PAYOUT", "CASHOUT"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
