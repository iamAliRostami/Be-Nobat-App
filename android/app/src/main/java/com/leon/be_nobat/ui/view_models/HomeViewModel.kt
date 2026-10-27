package com.leon.be_nobat.ui.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.data.remote.ApiPage
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.domain.model.Category
import com.leon.be_nobat.domain.model.TodaySlotItem
import com.leon.be_nobat.domain.usecase.GetCategoriesUseCase
import com.leon.be_nobat.domain.usecase.GetSlotsUseCase
import com.leon.be_nobat.domain.usecase.ListBranchesUseCase
import com.leon.be_nobat.domain.usecase.ListBranchServicesUseCase
import com.leon.be_nobat.domain.usecase.ListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.ListFavoritesUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.helpers.PersianUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BusinessSortOption {
    DEFAULT,
    RATING,
    BRANCHES,
    SERVICES,
    NAME
}

data class HomeUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val rawItems: List<Business> = emptyList(),
    val items: List<Business> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val query: String = "",
    val city: String = "",
    val category: String = "",
    val categories: List<Category> = emptyList(),
    val sortOption: BusinessSortOption = BusinessSortOption.DEFAULT,
    val filterInstantOnly: Boolean = false,
    val error: String? = null,
    val favoriteSnapshot: Map<String, Boolean> = emptyMap(),
    val loadingTodaySlots: Boolean = false,
    val todaySlots: List<TodaySlotItem> = emptyList(),
    val todaySlotsError: String? = null,
    val todaySlotsDate: String = "",
    val todaySlotsForTomorrow: Boolean = false,
)

class HomeViewModel(
    private val listBusinesses: ListBusinessesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val listFavorites: ListFavoritesUseCase? = null,
    private val getCategoriesUseCase: GetCategoriesUseCase? = null,
    private val listBranchesUseCase: ListBranchesUseCase? = null,
    private val listBranchServicesUseCase: ListBranchServicesUseCase? = null,
    private val getSlotsUseCase: GetSlotsUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadCategories()
        refresh(initial = true)
    }

    fun loadCategories() {
        val useCase = getCategoriesUseCase ?: return
        viewModelScope.launch {
            useCase.invoke().onSuccess { result ->
                val list = result.items.filter { it.kind.equals("Business", ignoreCase = true) }
                    .sortedBy { it.sortOrder }
                _state.value = _state.value.copy(
                    categories = if (list.isNotEmpty()) list else result.items
                )
            }
        }
    }

    fun refresh(initial: Boolean) {
        loadCategories()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            val s = _state.value
            var favMap = s.favoriteSnapshot
            listFavorites?.invoke()?.onSuccess { favPage ->
                favMap = favPage.items.associate { it.id to true }
            }
            _state.value = _state.value.copy(favoriteSnapshot = favMap)
            listBusinesses(s.query.ifBlank { null }, s.city.ifBlank { null }, s.category.ifBlank { null }, page = 1)
                .onSuccess { page: ApiPage<Business> ->
                    val current = _state.value
                    val filtered = applyFilterAndSort(page.items, current.sortOption, current.filterInstantOnly, favMap)
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false,
                        rawItems = page.items,
                        items = filtered,
                        favoriteSnapshot = favMap,
                        page = 1, total = page.total,
                        hasMore = page.items.size < page.total,
                        error = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = e.message)
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            listBusinesses(s.query.ifBlank { null }, s.city.ifBlank { null }, s.category.ifBlank { null }, page = s.page + 1)
                .onSuccess { page ->
                    val current = _state.value
                    val newRaw = current.rawItems + page.items
                    val filtered = applyFilterAndSort(newRaw, current.sortOption, current.filterInstantOnly, current.favoriteSnapshot)
                    _state.value = current.copy(
                        loading = false,
                        rawItems = newRaw,
                        items = filtered,
                        page = current.page + 1,
                        hasMore = newRaw.size < page.total,
                    )
                }
                .onFailure { e ->
                    _state.value = s.copy(loading = false, error = e.message)
                }
        }
    }

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun updateCity(city: String) {
        _state.value = _state.value.copy(city = city)
    }

    fun updateCategory(cat: String) {
        _state.value = _state.value.copy(category = cat)
    }

    fun setSortAndFilter(sortOption: BusinessSortOption, instantOnly: Boolean) {
        val s = _state.value
        val filtered = applyFilterAndSort(s.rawItems, sortOption, instantOnly, s.favoriteSnapshot)
        _state.value = s.copy(sortOption = sortOption, filterInstantOnly = instantOnly, items = filtered)
    }

    private fun applyFilterAndSort(
        source: List<Business>,
        sortOption: BusinessSortOption,
        instantOnly: Boolean,
        favorites: Map<String, Boolean> = _state.value.favoriteSnapshot,
    ): List<Business> {
        var result = if (instantOnly) source.filter { !it.requiresApproval } else source
        result = when (sortOption) {
            BusinessSortOption.DEFAULT -> result
            BusinessSortOption.RATING -> result.sortedByDescending { it.rating ?: 0.0 }
            BusinessSortOption.BRANCHES -> result.sortedByDescending { it.branchCount }
            BusinessSortOption.SERVICES -> result.sortedByDescending { it.serviceCount }
            BusinessSortOption.NAME -> result.sortedBy { it.name }
        }
        if (favorites.isNotEmpty()) {
            result = result.sortedByDescending { favorites[it.id] == true }
        }
        return result
    }

    fun loadTodaySlots(forTomorrow: Boolean = false) {
        val targetDate = if (forTomorrow) getTomorrowIso() else JalaliDateHelper.getTodayIso()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loadingTodaySlots = true,
                todaySlotsError = null,
                todaySlotsDate = targetDate,
                todaySlotsForTomorrow = forTomorrow,
            )
            try {
                var businesses = _state.value.rawItems
                if (businesses.isEmpty()) {
                    val pageRes = listBusinesses(null, null, null, page = 1)
                    businesses = pageRes.getOrNull()?.items.orEmpty()
                }

                val results = mutableListOf<TodaySlotItem>()
                val sdfTehran = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Tehran")
                }
                val nowTime = sdfTehran.format(Date())

                // Check up to top 6 businesses
                val sampleBusinesses = businesses.take(6)
                for (biz in sampleBusinesses) {
                    val branches = listBranchesUseCase?.invoke(biz.id)?.getOrNull()?.items.orEmpty()
                    val branch = branches.firstOrNull() ?: continue
                    val services = listBranchServicesUseCase?.invoke(biz.id, branch.id)?.getOrNull()?.items.orEmpty()
                    val selectedServices = services.take(2)
                    for (srv in selectedServices) {
                        val slotsResp = getSlotsUseCase?.invoke(biz.id, branch.id, srv.id, targetDate, null)?.getOrNull()
                        val rawSlots = slotsResp?.slots.orEmpty()
                        for (slot in rawSlots) {
                            if (!forTomorrow && slot.startsAt <= nowTime) {
                                continue
                            }
                            val rawTime = slot.startsAt.substringAfter("T").take(5)
                            val persianTime = PersianUtils.toPersianDigits(rawTime)
                            results.add(
                                TodaySlotItem(
                                    businessId = biz.id,
                                    businessName = biz.name,
                                    category = biz.category,
                                    branchId = branch.id,
                                    branchName = branch.name,
                                    serviceId = srv.id,
                                    serviceName = srv.name,
                                    serviceDurationMinutes = srv.durationMinutes,
                                    price = srv.price,
                                    currency = srv.currency,
                                    resourceId = slot.resourceId,
                                    resourceName = slot.resourceName,
                                    startsAt = slot.startsAt,
                                    endsAt = slot.endsAt,
                                    timeLabel = persianTime,
                                    requiresApproval = biz.requiresApproval,
                                )
                            )
                        }
                    }
                }

                results.sortBy { it.startsAt }
                _state.value = _state.value.copy(
                    loadingTodaySlots = false,
                    todaySlots = results,
                    todaySlotsError = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingTodaySlots = false,
                    todaySlotsError = e.message,
                )
            }
        }
    }

    private fun getTomorrowIso(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun toggleFavoriteFor(businessId: String, isFav: Boolean) {
        viewModelScope.launch {
            toggleFavorite(businessId, isFav).onSuccess {
                val snap = _state.value.favoriteSnapshot.toMutableMap()
                val newFav = !isFav
                if (newFav) snap[businessId] = true else snap.remove(businessId)
                val reordered = applyFilterAndSort(_state.value.rawItems, _state.value.sortOption, _state.value.filterInstantOnly, snap)
                _state.value = _state.value.copy(favoriteSnapshot = snap, items = reordered)
            }
        }
    }

    fun refreshFavoritesOnly() {
        val useCase = listFavorites ?: return
        viewModelScope.launch {
            useCase.invoke().onSuccess { favPage ->
                val favMap = favPage.items.associate { it.id to true }
                if (favMap != _state.value.favoriteSnapshot) {
                    val reordered = applyFilterAndSort(_state.value.rawItems, _state.value.sortOption, _state.value.filterInstantOnly, favMap)
                    _state.value = _state.value.copy(favoriteSnapshot = favMap, items = reordered)
                }
            }
        }
    }
}
