package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Dao
interface RiderDao {
    @Query("SELECT * FROM rider_profile WHERE id = 1")
    fun getProfileFlow(): Flow<RiderProfile?>

    @Query("SELECT * FROM rider_profile WHERE id = 1")
    suspend fun getProfile(): RiderProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: RiderProfile)
    
    @Update
    suspend fun updateProfile(profile: RiderProfile)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM delivery_orders ORDER BY id DESC")
    fun getAllOrdersFlow(): Flow<List<DeliveryOrder>>

    @Query("SELECT * FROM delivery_orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: String): DeliveryOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<DeliveryOrder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: DeliveryOrder)

    @Update
    suspend fun updateOrder(order: DeliveryOrder)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<WalletTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransaction)
}

@Database(
    entities = [RiderProfile::class, DeliveryOrder::class, WalletTransaction::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun riderDao(): RiderDao
    abstract fun orderDao(): OrderDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rider_hustle_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate DB asynchronously on creation
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getDatabase(context)
                            populateInitialData(database)
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            // 1. Insert default rider profile
            val defaultProfile = RiderProfile()
            db.riderDao().insertProfile(defaultProfile)

            // 2. Insert interesting seed orders
            val initialOrders = listOf(
                DeliveryOrder(
                    id = "#88219",
                    customerName = "麦当劳 (南京西路店)",
                    phone = "138-1644-8821",
                    pickupAddress = "麦当劳 (南京西路店)",
                    pickupDistance = 0.8,
                    deliveryAddress = "旭日公寓 - 3号楼",
                    deliveryDistance = 3.2,
                    estTimeMinutes = 25,
                    itemCount = 3,
                    price = 18.50,
                    subsidy = 3.00,
                    remark = "请放前台，食品保温袋包装。速达！",
                    status = "PENDING_GRAB",
                    specialTag = "即时即送"
                ),
                DeliveryOrder(
                    id = "#88224",
                    customerName = "永辉超市 (环球港店)",
                    phone = "189-3051-8224",
                    pickupAddress = "永辉超市 (环球港店)",
                    pickupDistance = 1.5,
                    deliveryAddress = "长征欣苑 - 12号楼",
                    deliveryDistance = 6.8,
                    estTimeMinutes = 40,
                    itemCount = 12,
                    price = 42.00,
                    subsidy = 8.50,
                    remark = "超重货物(>10kg)，请携带大规格后备箱！物品包含5L大豆油和成箱矿泉水。",
                    status = "PENDING_GRAB",
                    specialTag = "超重大件"
                ),
                DeliveryOrder(
                    id = "#88251",
                    customerName = "喜茶 (静安大悦城店)",
                    phone = "135-2415-5182",
                    pickupAddress = "喜茶 (静安大悦城店)",
                    pickupDistance = 1.2,
                    deliveryAddress = "凯旋阁小区 - 2单元",
                    deliveryDistance = 2.1,
                    estTimeMinutes = 18,
                    itemCount = 2,
                    price = 12.50,
                    subsidy = 1.00,
                    remark = "请去冰无糖，多带一份吸管，谢谢师傅",
                    status = "PENDING_GRAB",
                    specialTag = "饮品常温"
                ),
                DeliveryOrder(
                    id = "#88264",
                    customerName = "肯德基 (打浦桥日月光店)",
                    phone = "177-1249-1664",
                    pickupAddress = "肯德基 (打浦桥日月光店)",
                    pickupDistance = 0.5,
                    deliveryAddress = "汇龙新城 - A栋 402室",
                    deliveryDistance = 1.5,
                    estTimeMinutes = 15,
                    itemCount = 4,
                    price = 14.00,
                    subsidy = 2.00,
                    remark = "无电梯，4层。辛苦师傅爬个楼，敲门放门口即可！",
                    status = "PENDING_GRAB",
                    specialTag = "今日速递"
                ),
                DeliveryOrder(
                    id = "#88280",
                    customerName = "老北京涮羊肉餐厅",
                    phone = "186-0112-9280",
                    pickupAddress = "老北京涮羊肉 (长宁店)",
                    pickupDistance = 2.4,
                    deliveryAddress = "延安高架商务中心 5楼A5-02",
                    deliveryDistance = 5.6,
                    estTimeMinutes = 35,
                    itemCount = 8,
                    price = 36.00,
                    subsidy = 5.00,
                    remark = "汤汁较多！高温包装，小心撒漏！有夜间行车安全补贴。",
                    status = "PENDING_GRAB",
                    specialTag = "高温汤汁"
                )
            )
            db.orderDao().insertOrders(initialOrders)

            // 3. Add seed completed history transactions to populate wallet nicely initially!
            val initialTransactions = listOf(
                WalletTransaction(
                    orderId = "#88102",
                    amount = 16.50,
                    type = "DELIVERY_PAYOUT",
                    description = "完成订单 #88102（配送费:13.5 + 补贴:3.0）",
                    timestamp = System.currentTimeMillis() - 7200000 // 2 hours ago
                ),
                WalletTransaction(
                    orderId = "#88095",
                    amount = 22.00,
                    type = "DELIVERY_PAYOUT",
                    description = "完成订单 #88095（配送费:18.0 + 补贴:4.0）",
                    timestamp = System.currentTimeMillis() - 18000000 // 5 hours ago
                ),
                WalletTransaction(
                    orderId = "#88081",
                    amount = 14.50,
                    type = "DELIVERY_PAYOUT",
                    description = "完成订单 #88081（配送费:12.5 + 补贴:2.0）",
                    timestamp = System.currentTimeMillis() - 36000000 // 10 hours ago
                ),
                WalletTransaction(
                    amount = -200.00,
                    type = "CASHOUT",
                    description = "余额提现至银行卡（尾号4302）",
                    timestamp = System.currentTimeMillis() - 86400000 // 1 day ago
                )
            )
            for (tx in initialTransactions) {
                db.transactionDao().insertTransaction(tx)
            }
        }
    }
}
