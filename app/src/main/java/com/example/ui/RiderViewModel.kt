package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DeliveryOrder
import com.example.data.RiderProfile
import com.example.data.WalletTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RiderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val orderDao = db.orderDao()
    private val riderDao = db.riderDao()
    private val transactionDao = db.transactionDao()

    // UI state streams from Room
    val orders: StateFlow<List<DeliveryOrder>> = orderDao.getAllOrdersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val profile: StateFlow<RiderProfile?> = riderDao.getProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val transactions: StateFlow<List<WalletTransaction>> = transactionDao.getAllTransactionsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Auxiliary state for login, navigation, calling, active detail order
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _selectedOrder = MutableStateFlow<DeliveryOrder?>(null)
    val selectedOrder: StateFlow<DeliveryOrder?> = _selectedOrder.asStateFlow()

    // Navigation map simulation state
    private val _navigationState = MutableStateFlow<NavigationSimState?>(null)
    val navigationState: StateFlow<NavigationSimState?> = _navigationState.asStateFlow()

    // Call screen simulation state
    private val _activeCallNumber = MutableStateFlow<String?>(null)
    val activeCallNumber: StateFlow<String?> = _activeCallNumber.asStateFlow()

    private val _currentSubTab = MutableStateFlow(0)
    val currentSubTab: StateFlow<Int> = _currentSubTab.asStateFlow()

    fun setCurrentSubTab(index: Int) {
        _currentSubTab.value = index
    }

    init {
        // Double safeguard: if DB is opened and profiles/orders are empty, pre-populate right now
        viewModelScope.launch(Dispatchers.IO) {
            val existingProfile = riderDao.getProfile()
            if (existingProfile == null) {
                // Database was not populated automatically, let's insert initial data now
                riderDao.insertProfile(RiderProfile())
                
                // Add default seed orders
                val seedOrders = getSeedOrders()
                orderDao.insertOrders(seedOrders)
                
                // Add initial transactions
                val seedTransactions = getSeedTransactions()
                for (tx in seedTransactions) {
                    transactionDao.insertTransaction(tx)
                }
            }
        }
    }

    fun login(emailOrPhone: String, inputPhone: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val prof = riderDao.getProfile() ?: RiderProfile()
            val updated = if (emailOrPhone.contains("@")) {
                prof.copy(email = emailOrPhone, phone = inputPhone ?: prof.phone)
            } else {
                prof.copy(phone = emailOrPhone)
            }
            riderDao.updateProfile(updated)
            _isLoggedIn.value = true
        }
    }

    fun loginWithEmail(emailAddress: String, passwordEntered: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val prof = riderDao.getProfile() ?: RiderProfile()
            if (prof.email.trim().equals(emailAddress.trim(), ignoreCase = true) && prof.password.trim() == passwordEntered.trim()) {
                _isLoggedIn.value = true
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onFailure("密码不正确或此邮箱未注册！")
                }
            }
        }
    }

    fun register(email: String, name: String, phone: String, passwordEntered: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prof = riderDao.getProfile() ?: RiderProfile()
                val updated = prof.copy(
                    email = email.trim(),
                    name = name.trim(),
                    phone = phone.trim(),
                    password = passwordEntered.trim(),
                    totalBalance = 0.0,
                    todayEarnings = 0.0,
                    withdrawableBalance = 0.0,
                    completedOrdersCount = 0
                )
                riderDao.updateProfile(updated)
                _isLoggedIn.value = true
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e.localizedMessage ?: "注册发生异常错误")
                }
            }
        }
    }

    fun logout() {
        _isLoggedIn.value = false
    }

    fun selectOrder(order: DeliveryOrder?) {
        _selectedOrder.value = order
    }

    // Grab/Accept an order
    fun grabOrder(order: DeliveryOrder, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = order.copy(
                status = "DELIVERING",
                acceptTime = System.currentTimeMillis()
            )
            orderDao.updateOrder(updated)
            
            // If the user selects the order, update the selected reference
            if (_selectedOrder.value?.id == order.id) {
                _selectedOrder.value = updated
            }
            
            _currentSubTab.value = 1
            
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    // Confirm Pickup at store with photo留存
    fun confirmPickupWithPhoto(order: DeliveryOrder, photoUri: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = order.copy(
                pickupPhoto = photoUri
            )
            orderDao.updateOrder(updated)
            
            if (_selectedOrder.value?.id == order.id) {
                _selectedOrder.value = updated
            }
            
            // Log simulated high-efficiency compressed upload for backend auditing
            postUploadedImage(photoUri, isCompressed = true, compressionRatio = 0.32f, sizeKb = 118)
            
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    // Confirm Delivery to customer with photo留存
    fun confirmDeliveryWithPhoto(order: DeliveryOrder, photoUri: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = order.copy(
                deliveryPhoto = photoUri
            )
            orderDao.updateOrder(updated)
            
            if (_selectedOrder.value?.id == order.id) {
                _selectedOrder.value = updated
            }
            
            // Log simulated high-efficiency compressed upload for backend auditing
            postUploadedImage(photoUri, isCompressed = true, compressionRatio = 0.28f, sizeKb = 94)
            
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    // Complete an order delivery
    fun completeDelivery(order: DeliveryOrder, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = order.copy(
                status = "COMPLETED",
                completeTime = System.currentTimeMillis()
            )
            orderDao.updateOrder(updated)

            // Update Rider Profile balance, stats, etc.
            val currentProfile = riderDao.getProfile() ?: RiderProfile()
            val totalPayout = order.price + order.subsidy
            
            val updatedProfile = currentProfile.copy(
                totalBalance = currentProfile.totalBalance + totalPayout,
                todayEarnings = currentProfile.todayEarnings + totalPayout,
                withdrawableBalance = currentProfile.withdrawableBalance + totalPayout,
                completedOrdersCount = currentProfile.completedOrdersCount + 1,
                // Subtle rating bump just for fun
                rating = minOf(5.0f, currentProfile.rating + 0.001f)
            )
            riderDao.updateProfile(updatedProfile)

            // Log Transaction
            val tx = WalletTransaction(
                orderId = order.id,
                amount = totalPayout,
                type = "DELIVERY_PAYOUT",
                description = "配送达 ${order.customerName}（配送单费: ¥${"%.2f".format(order.price)} + 补贴: ¥${"%.2f".format(order.subsidy)}）"
            )
            transactionDao.insertTransaction(tx)

            if (_selectedOrder.value?.id == order.id) {
                _selectedOrder.value = updated
            }

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    // Cashout balance
    fun withdraw(amount: Double, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = riderDao.getProfile() ?: RiderProfile()
            if (amount > currentProfile.withdrawableBalance) {
                withContext(Dispatchers.Main) {
                    onFailure("提现金额不能大于可提现余额！")
                }
                return@launch
            }

            val updatedProfile = currentProfile.copy(
                totalBalance = currentProfile.totalBalance - amount,
                withdrawableBalance = currentProfile.withdrawableBalance - amount
            )
            riderDao.updateProfile(updatedProfile)

            val tx = WalletTransaction(
                amount = -amount,
                type = "CASHOUT",
                description = "提取余额资金 ¥${"%.2f".format(amount)} 到招商银行尾号(4102)"
            )
            transactionDao.insertTransaction(tx)

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    // Toggle rider state (Online/Offline)
    fun toggleOnlineStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = riderDao.getProfile() ?: RiderProfile()
            val newState = !currentProfile.isOnline
            val newText = if (newState) "在线接单" else "休息/离线"
            val updated = currentProfile.copy(
                isOnline = newState,
                onlineStatusText = newText
            )
            riderDao.updateProfile(updated)
        }
    }

    fun setRiderStatus(isOnline: Boolean, statusText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = riderDao.getProfile() ?: RiderProfile()
            val updated = currentProfile.copy(
                isOnline = isOnline,
                onlineStatusText = statusText
            )
            riderDao.updateProfile(updated)
        }
    }

    fun updateProfile(newProfile: RiderProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            riderDao.updateProfile(newProfile)
        }
    }

    // Switch custom theme
    fun changeTheme(themeName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = riderDao.getProfile() ?: RiderProfile()
            val updated = currentProfile.copy(selectedTheme = themeName)
            riderDao.updateProfile(updated)
        }
    }

    // Simulate Calling Overlay
    fun startCall(phoneNumber: String) {
        _activeCallNumber.value = phoneNumber
    }

    fun endCall() {
        _activeCallNumber.value = null
    }

    // Simulate Navigation Map Animation
    fun startNavigation(order: DeliveryOrder) {
        viewModelScope.launch {
            val toStore = order.pickupPhoto == null
            _navigationState.value = NavigationSimState(
                orderId = order.id,
                originAddress = if (toStore) "骑士大本营中心" else order.pickupAddress,
                destinationAddress = if (toStore) order.pickupAddress else order.deliveryAddress,
                distanceRemaining = if (toStore) order.pickupDistance else order.deliveryDistance,
                progressDegrees = 0f,
                completed = false
            )
        }
    }

    fun updateNavigationProgress(progressDegrees: Float, distanceRemaining: Double, completed: Boolean = false) {
        val current = _navigationState.value ?: return
        _navigationState.value = current.copy(
            progressDegrees = progressDegrees,
            distanceRemaining = distanceRemaining,
            completed = completed
        )
    }

    fun exitNavigation() {
        _navigationState.value = null
    }

    // Re-fill / refresh available orders for sandbox play!
    fun resetSandboxOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            val seedOrders = getSeedOrders()
            // Clear current then insert
            for (order in seedOrders) {
                orderDao.insertOrder(order)
            }
        }
    }

    private fun getSeedOrders(): List<DeliveryOrder> {
        return listOf(
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
            ),
            DeliveryOrder(
                id = "#88292",
                customerName = "星巴克 (静安核心写字楼店)",
                phone = "156-4315-9922",
                pickupAddress = "星巴克 (静安核心写字楼店)",
                pickupDistance = 0.3,
                deliveryAddress = "晶安阁写字楼段A座 1805室",
                deliveryDistance = 1.2,
                estTimeMinutes = 12,
                itemCount = 1,
                price = 11.50,
                subsidy = 1.50,
                remark = "热美式一杯，门口放着打个电话即可，谢谢",
                status = "PENDING_GRAB",
                specialTag = "即时即送"
            )
        )
    }

    private fun getSeedTransactions(): List<WalletTransaction> {
        return listOf(
            WalletTransaction(
                orderId = "#88102",
                amount = 16.50,
                type = "DELIVERY_PAYOUT",
                description = "完成订单 #88102（配送费:13.5 + 补贴:3.0）",
                timestamp = System.currentTimeMillis() - 7200000
            ),
            WalletTransaction(
                orderId = "#88095",
                amount = 22.00,
                type = "DELIVERY_PAYOUT",
                description = "完成订单 #88095（配送费:18.0 + 补贴:4.0）",
                timestamp = System.currentTimeMillis() - 18000000
            ),
            WalletTransaction(
                orderId = "#88081",
                amount = 14.50,
                type = "DELIVERY_PAYOUT",
                description = "完成订单 #88081（配送费:12.5 + 补贴:2.0）",
                timestamp = System.currentTimeMillis() - 36000000
            ),
            WalletTransaction(
                amount = -200.00,
                type = "CASHOUT",
                description = "余额提现至银行卡（尾号4302）",
                timestamp = System.currentTimeMillis() - 86400000
            )
        )
    }

    // Interactive Customer Chat dialogue simulation (Requirement: 增加订单对话功能)
    private val _orderChats = MutableStateFlow<Map<String, List<OrderMessage>>>(emptyMap())
    val orderChats: StateFlow<Map<String, List<OrderMessage>>> = _orderChats.asStateFlow()

    fun sendOrderMessage(orderId: String, content: String) {
        viewModelScope.launch {
            val currentMap = _orderChats.value.toMutableMap()
            val existing = currentMap[orderId]?.toMutableList() ?: mutableListOf()
            val riderMsg = OrderMessage(orderId = orderId, sender = "RIDER", content = content)
            existing.add(riderMsg)
            currentMap[orderId] = existing
            _orderChats.value = currentMap

            // Autopilot consumer replies trigger
            kotlinx.coroutines.delay(1200)
            val autoReplies = listOf(
                "好的好的，收到！辛苦啦，注意行车安全哦！",
                "电梯需要刷卡的话，可以先放前台柜子里，谢谢！",
                "抱歉骑手哥，刚才开了免打扰，直接挂在大门挂钩上就好，辛苦！",
                "我知道了，马上到门口等，慢点开不着急哈。",
                "好，收到！感谢骑士老哥派送！"
            )
            val replyText = autoReplies.shuffled().first()
            val customerMsg = OrderMessage(orderId = orderId, sender = "CUSTOMER", content = replyText)
            
            val updatedMap = _orderChats.value.toMutableMap()
            val updatedList = updatedMap[orderId]?.toMutableList() ?: mutableListOf()
            updatedList.add(customerMsg)
            updatedMap[orderId] = updatedList
            _orderChats.value = updatedMap
        }
    }

    // Modern High-Precision Geolocation, Navigation Fused, and Compressed Image Upload backend simulator (Requirements 1-5)
    private val _locationLogs = MutableStateFlow<List<RiderLocationLog>>(emptyList())
    val locationLogs: StateFlow<List<RiderLocationLog>> = _locationLogs.asStateFlow()

    private val _uploadedImages = MutableStateFlow<List<UploadedImageLog>>(emptyList())
    val uploadedImages: StateFlow<List<UploadedImageLog>> = _uploadedImages.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(true)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private val _detectedWifiList = MutableStateFlow<List<String>>(emptyList())
    val detectedWifiList: StateFlow<List<String>> = _detectedWifiList.asStateFlow()

    init {
        // Continuous high accuracy tracking with Kalman Filtering & Wi-Fi Scan simulation loop
        viewModelScope.launch(Dispatchers.Default) {
            val filterLat = SimpleKalmanFilter(0.00001, 0.00008)
            val filterLng = SimpleKalmanFilter(0.00001, 0.00008)
            
            // Base starting position of the rider (around People's Square, Shanghai center)
            var currentLat = 31.2304
            var currentLng = 121.4737
            
            val wifiPool = listOf(
                "ChinaNet-HustleRider-Express", "TP-LINK_5G_DeliveryHub", "Alipay_EasyGPS_Aux",
                "ChinaUnicom-STA-882", "BaiduMapFusedLoc_24G", "GD_LocationCorrection_Beacon",
                "Mi_Router_RiderRoom", "ChaoShi-Store-WiFi", "Starbucks-Guest-FreePhone", "Community-Indoor-5G"
            )

            while (true) {
                kotlinx.coroutines.delay(2500) // continuous location interval rate
                if (_isTrackingActive.value) {
                    // Raw noisy walk coordinate representing standard satellite raw outputs with atmospheric drifts
                    val rawDriftLat = (kotlin.random.Random.nextDouble() - 0.5) * 0.0012
                    val rawDriftLng = (kotlin.random.Random.nextDouble() - 0.5) * 0.0012
                    
                    val rawLat = currentLat + rawDriftLat
                    val rawLng = currentLng + rawDriftLng
                    
                    // Filtered continuous positions calculation (Kalman filtering algorithm implementation)
                    val filteredLat = filterLat.update(rawLat)
                    val filteredLng = filterLng.update(rawLng)
                    
                    // Slow reference drift paths
                    currentLat = currentLat + (kotlin.random.Random.nextDouble() - 0.5) * 0.0002
                    currentLng = currentLng + (kotlin.random.Random.nextDouble() - 0.5) * 0.0002
                    
                    // Wi-Fi scanning counts for assistive positioning (Enabling sensor-assisted location)
                    val scanCount = kotlin.random.Random.nextInt(4, 9)
                    val scannedWifis = wifiPool.shuffled().take(scanCount)
                    _detectedWifiList.value = scannedWifis
                    
                    val point = RiderLocationLog(
                        latitude = filteredLat,
                        rawLatitude = rawLat,
                        longitude = filteredLng,
                        rawLongitude = rawLng,
                        altitude = 54.0 + (kotlin.random.Random.nextDouble() - 0.5) * 1.5,
                        accuracy = 3.0f + kotlin.random.Random.nextFloat() * 1.5f, // 3.0m - 4.5m ultra-high precision
                        wifiCount = scanCount
                    )
                    
                    val currentList = _locationLogs.value.toMutableList()
                    currentList.add(0, point)
                    if (currentList.size > 20) {
                        currentList.removeAt(currentList.size - 1)
                    }
                    _locationLogs.value = currentList
                }
            }
        }
    }

    fun toggleTracking(enabled: Boolean) {
        _isTrackingActive.value = enabled
    }

    // Backend receiving interface endpoint simulating picture post delivery attachments storage
    fun postUploadedImage(filePath: String, isCompressed: Boolean, compressionRatio: Float, sizeKb: Int) {
        viewModelScope.launch {
            val randomImages = listOf(
                "https://images.unsplash.com/photo-1558981806-ec527fa84c39?q=80&w=260", 
                "https://images.unsplash.com/photo-1540340144394-4334f490b343?q=80&w=260",
                "https://images.unsplash.com/photo-1551836022-d5d88e9218df?q=80&w=260", 
                "https://images.unsplash.com/photo-1507034589631-9433cc6bc453?q=80&w=260"
            )
            val demoUrl = randomImages.shuffled().first()
            val newLog = UploadedImageLog(
                localPath = filePath,
                isCompressed = isCompressed,
                compressionRatio = compressionRatio,
                fileSizeKb = sizeKb,
                downloadUrl = demoUrl
            )
            val current = _uploadedImages.value.toMutableList()
            current.add(0, newLog)
            _uploadedImages.value = current
        }
    }
}

// Kalman Filter helper class to smooth raw GPS location coordinates drift continuously
class SimpleKalmanFilter(private val processNoise: Double = 0.00002, private val measurementNoise: Double = 0.0008) {
    private var estimate: Double? = null
    private var errorCovariance: Double = 1.0

    fun update(measurement: Double): Double {
        val currentEstimate = estimate
        if (currentEstimate == null) {
            estimate = measurement
            return measurement
        }
        val predictedErrorCovariance = errorCovariance + processNoise
        val kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise)
        val newEstimate = currentEstimate + kalmanGain * (measurement - currentEstimate)
        
        estimate = newEstimate
        errorCovariance = (1.0 - kalmanGain) * predictedErrorCovariance
        return newEstimate
    }
}

data class RiderLocationLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val latitude: Double,
    val rawLatitude: Double,
    val longitude: Double,
    val rawLongitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val wifiCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class UploadedImageLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val localPath: String,
    val isCompressed: Boolean,
    val compressionRatio: Float,
    val fileSizeKb: Int,
    val downloadUrl: String,
    val uploadTime: Long = System.currentTimeMillis()
)

data class NavigationSimState(
    val orderId: String,
    val originAddress: String,
    val destinationAddress: String,
    val distanceRemaining: Double,
    val progressDegrees: Float,
    val completed: Boolean
)

data class OrderMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val orderId: String,
    val sender: String, // "RIDER" "CUSTOMER"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
