package com.leekleak.trafficlight.ui.plans

import androidx.compose.animation.core.snap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leekleak.trafficlight.charts.model.BarData
import com.leekleak.trafficlight.database.AppPreferenceRepo
import com.leekleak.trafficlight.database.AppUsage
import com.leekleak.trafficlight.database.DataPlan
import com.leekleak.trafficlight.database.DataPlanDao
import com.leekleak.trafficlight.database.DataPlanSnapshot
import com.leekleak.trafficlight.model.NetworkUsageManager
import com.leekleak.trafficlight.util.MiniCardState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DataPlansUiState(
    val activePlans: List<DataPlan> = emptyList(),
    val planSnapshots: Map<DataPlan, DataPlanSnapshot> = emptyMap(),
    val plan: DataPlan? = null,
    val dataSafety: MiniCardState = MiniCardState.NEUTRAL,
    val trend: Int = 0,
    val todayBudget: Long = 0,
    val remainingDailyBudget: Long = 0,
    val weekUsage: List<BarData> = emptyList(),
    val topApps: List<AppUsage> = emptyList(),
    val adsEnabled: Boolean = false,
    val shizukuHint: Boolean = false,
    val shizukuTracking: Boolean = false,
)

class DataPlansVM(
    val dataPlansLogic: DataPlanLogic,
    val networkUsageManager: NetworkUsageManager,
    val appPreferenceRepo: AppPreferenceRepo,
    dataPlanDao: DataPlanDao,
): ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    fun refresh() = refreshTrigger.tryEmit(Unit)

    private val selectedDataPlan = MutableStateFlow<DataPlan?>(null)
    fun selectDataPlan(dataPlan: DataPlan?) {
        selectedDataPlan.value = dataPlan
    }

    init {
        viewModelScope.launch {
            dataPlanDao.activePlansFlow.collect { plans ->
                if (plans.isNotEmpty()) {
                    if (selectedDataPlan.value == null) {
                        selectedDataPlan.value = plans.first()
                    } else if (!plans.contains(selectedDataPlan.value)) {
                        selectedDataPlan.value =
                            plans.find { it.hashedSubscriberID == selectedDataPlan.value?.hashedSubscriberID }
                                ?: plans.first()
                    }
                }
            }
        }
    }

    private val planSnapshotFlow = combine(dataPlanDao.activePlansFlow, refreshTrigger) { list, _ ->
        list.associateWith { it.getUsageSnapshot(networkUsageManager) }
    }

    private val planFlow = combine(selectedDataPlan, planSnapshotFlow) { plan, snapshots ->
        val snapshot = snapshots[plan]
        if (plan != null && snapshot != null) {
            plan to snapshot
        }
        else null
    }.filterNotNull().distinctUntilChanged()

    private val dataSafetyFlow = planFlow.map { (plan, snapshot) -> dataPlansLogic.getDataSafety(plan, snapshot) }.distinctUntilChanged()
    private val todayBudgetFlow = planFlow.map { (plan, snapshot) -> dataPlansLogic.getRemainingDailyBudgetToday(plan, snapshot) }.distinctUntilChanged()
    private val remainingDailyBudgetFlow = planFlow.map { (plan, snapshot) -> dataPlansLogic.getRemainingDailyBudget(plan, snapshot) }.distinctUntilChanged()
    private val trendFlow = planFlow.map { (plan, _) -> dataPlansLogic.getTrend(plan) }.distinctUntilChanged()
    private val weekUsageFlow = planFlow.map { (plan, _) -> dataPlansLogic.getWeekUsage(plan) }.distinctUntilChanged()
    private val topAppsFlow = planFlow.map { (plan, _) -> dataPlansLogic.getTopAppUsage(plan) }.distinctUntilChanged()


    val uiState = combine(
        dataPlanDao.activePlansFlow,
        planSnapshotFlow,
        selectedDataPlan,
        dataSafetyFlow,
        trendFlow,
        todayBudgetFlow,
        remainingDailyBudgetFlow,
        weekUsageFlow,
        topAppsFlow,
        appPreferenceRepo.ads,
        appPreferenceRepo.shizukuHint,
        appPreferenceRepo.shizukuTracking
    ) { flows ->
        val activePlans = flows[0] as List<DataPlan>
        val planSnapshots = flows[1] as Map<DataPlan, DataPlanSnapshot>
        val plan = flows[2] as DataPlan?
        val safety = flows[3] as MiniCardState? ?: MiniCardState.NEUTRAL
        val trend = flows[4] as Int? ?: 0
        val today = flows[5] as Long? ?: 0L
        val remaining = flows[6] as Long? ?: 0L
        val week = flows[7] as List<BarData>
        val apps = flows[8] as List<AppUsage>
        val adsEnabled = flows[9] as Boolean
        val shizukuHint = flows[10] as Boolean
        val shizukuTracking = flows[11] as Boolean

        DataPlansUiState(
            activePlans = activePlans,
            plan = plan,
            planSnapshots = planSnapshots,
            dataSafety = safety,
            trend = trend,
            todayBudget = today,
            remainingDailyBudget = remaining,
            weekUsage = week,
            topApps = apps,
            adsEnabled = adsEnabled,
            shizukuHint = shizukuHint,
            shizukuTracking = shizukuTracking
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataPlansUiState())

    fun disableShizukuHint() = viewModelScope.launch { appPreferenceRepo.setShizukuHint(false) }
}
