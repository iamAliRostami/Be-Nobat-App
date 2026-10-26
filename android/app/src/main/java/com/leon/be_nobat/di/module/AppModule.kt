package com.leon.be_nobat.di.module

import com.leon.be_nobat.data.demo.*
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.data.local.CryptoManager
import com.leon.be_nobat.data.local.ThemeManager
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.data.repository.AuthRepositoryImpl
import com.leon.be_nobat.data.repository.BookingRepositoryImpl
import com.leon.be_nobat.data.repository.BusinessRepositoryImpl
import com.leon.be_nobat.data.repository.FavoriteRepositoryImpl
import com.leon.be_nobat.data.repository.ManagementRepositoryImpl
import com.leon.be_nobat.data.repository.PlatformRepositoryImpl
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import com.leon.be_nobat.domain.repository.AuthRepository
import com.leon.be_nobat.domain.repository.BookingRepository
import com.leon.be_nobat.domain.repository.BusinessRepository
import com.leon.be_nobat.domain.repository.FavoriteRepository
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.repository.PlatformRepository
import com.leon.be_nobat.domain.usecase.AdminApproveBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminContextUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateCustomerReviewUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateMembershipUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateServiceUseCase
import com.leon.be_nobat.domain.usecase.AdminAnalyticsUseCase
import com.leon.be_nobat.domain.usecase.AdminDashboardUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminDeleteMembershipUseCase
import com.leon.be_nobat.domain.usecase.AdminListAppointmentsUseCase
import com.leon.be_nobat.domain.usecase.AdminListAvailabilityUseCase
import com.leon.be_nobat.domain.usecase.AdminListBranchesUseCase
import com.leon.be_nobat.domain.usecase.AdminListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.AdminListCustomerReviewsUseCase
import com.leon.be_nobat.domain.usecase.AdminListMembershipsUseCase
import com.leon.be_nobat.domain.usecase.AdminListPendingCustomerReviewsUseCase
import com.leon.be_nobat.domain.usecase.AdminListResourceServicesUseCase
import com.leon.be_nobat.domain.usecase.AdminListReviewsUseCase
import com.leon.be_nobat.domain.usecase.AdminListResourcesUseCase
import com.leon.be_nobat.domain.usecase.AdminListServicesUseCase
import com.leon.be_nobat.domain.usecase.AdminListTeamUseCase
import com.leon.be_nobat.domain.usecase.AdminListCustomersUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateTeamMemberUseCase
import com.leon.be_nobat.domain.usecase.AdminCreateCustomerUseCase
import com.leon.be_nobat.domain.usecase.AdminTogglePersonActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminModerateReviewUseCase
import com.leon.be_nobat.domain.usecase.AdminSetAppointmentStatusUseCase
import com.leon.be_nobat.domain.usecase.AdminToggleBranchActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminToggleServiceActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateServiceUseCase
import com.leon.be_nobat.domain.usecase.CancelAppointmentUseCase
import com.leon.be_nobat.domain.usecase.ChangeEmailUseCase
import com.leon.be_nobat.domain.usecase.ChangePasswordUseCase
import com.leon.be_nobat.domain.usecase.CreateAppointmentUseCase
import com.leon.be_nobat.domain.usecase.CreateReviewUseCase
import com.leon.be_nobat.domain.usecase.DeleteAvatarUseCase
import com.leon.be_nobat.domain.usecase.GetBusinessUseCase
import com.leon.be_nobat.domain.usecase.GetCatalogUseCase
import com.leon.be_nobat.domain.usecase.GetCategoriesUseCase
import com.leon.be_nobat.domain.usecase.GetMeUseCase
import com.leon.be_nobat.domain.usecase.GetMyBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.GetSlotsUseCase
import com.leon.be_nobat.domain.usecase.ListAppointmentsUseCase
import com.leon.be_nobat.domain.usecase.ListBranchesUseCase
import com.leon.be_nobat.domain.usecase.ListBranchServicesUseCase
import com.leon.be_nobat.domain.usecase.ListBusinessReviewsUseCase
import com.leon.be_nobat.domain.usecase.ListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.ListFavoritesUseCase
import com.leon.be_nobat.domain.usecase.ListProvidersUseCase
import com.leon.be_nobat.domain.usecase.LoginUseCase
import com.leon.be_nobat.domain.usecase.LogoutUseCase
import com.leon.be_nobat.domain.usecase.PlatformCreateBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformCreateCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformCreateCategoryUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateCategoryUseCase
import com.leon.be_nobat.domain.usecase.PlatformCreateUserUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformDeleteCategoryUseCase
import com.leon.be_nobat.domain.usecase.PlatformListBusinessesUseCase
import com.leon.be_nobat.domain.usecase.PlatformListCatalogUseCase
import com.leon.be_nobat.domain.usecase.PlatformListCategoriesUseCase
import com.leon.be_nobat.domain.usecase.PlatformListUsersUseCase
import com.leon.be_nobat.domain.usecase.PlatformResetPasswordUseCase
import com.leon.be_nobat.domain.usecase.PlatformSetUserRolesUseCase
import com.leon.be_nobat.domain.usecase.PlatformToggleBusinessActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformToggleUserActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateUserUseCase
import com.leon.be_nobat.domain.usecase.PlatformListBusinessRequestsUseCase
import com.leon.be_nobat.domain.usecase.PlatformApproveBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.PlatformRejectBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.PlatformApproveBranchUseCase
import com.leon.be_nobat.domain.usecase.RegisterUseCase
import com.leon.be_nobat.domain.usecase.SubmitBusinessRequestUseCase
import com.leon.be_nobat.domain.usecase.ToggleFavoriteUseCase
import com.leon.be_nobat.domain.usecase.UpdateProfileUseCase
import com.leon.be_nobat.domain.usecase.UploadAvatarUseCase
import com.leon.be_nobat.helpers.App
import com.leon.be_nobat.helpers.PREFERENCES_NAME
import com.leon.be_nobat.ui.view_models.AppointmentsViewModel
import com.leon.be_nobat.ui.view_models.BookingViewModel
import com.leon.be_nobat.ui.view_models.BusinessDetailViewModel
import com.leon.be_nobat.ui.view_models.FavoritesViewModel
import com.leon.be_nobat.ui.view_models.HomeViewModel
import com.leon.be_nobat.ui.view_models.ProfileViewModel
import com.leon.be_nobat.ui.view_models.RegisterViewModel
import com.leon.be_nobat.ui.view_models.auth.AuthViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminBranchesViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminAnalyticsViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminCalendarViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminDashboardViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminReviewsViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminCustomersViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminTeamViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminServicesViewModel
import com.leon.be_nobat.ui.view_models.admin.AdminAvailabilityViewModel
import com.leon.be_nobat.ui.view_models.platform.PlatformBusinessesViewModel
import com.leon.be_nobat.ui.view_models.platform.PlatformCatalogViewModel
import com.leon.be_nobat.ui.view_models.platform.PlatformCategoriesViewModel
import com.leon.be_nobat.ui.view_models.platform.PlatformAccessViewModel
import com.leon.be_nobat.ui.view_models.platform.PlatformRequestsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val localStorageModule = module {
    single {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile(PREFERENCES_NAME) }
        )
    }
    single<ICryptoManager> { CryptoManager }
    single { TokenManager(get(), get(), get()) }
    single { ThemeManager(get()) }
}

val repositoryModule = module {
    single { com.leon.be_nobat.notifications.AppointmentNotificationMonitor(androidContext(), get(), get()) }
    if (BuildConfig.DEMO_MODE) {
        single { DemoDataStore(androidContext(), get()) }
        single<AuthRepository> { DemoAuthRepository(get(), get()) }
        single<BusinessRepository> { DemoBusinessRepository(get()) }
        single<BookingRepository> { DemoBookingRepository(get(), get()) }
        single<FavoriteRepository> { DemoFavoriteRepository(get(), get()) }
        single<ManagementRepository> { DemoManagementRepository(get(), get()) }
        single<PlatformRepository> { DemoPlatformRepository(get(), get()) }
    } else {
        single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
        single<BusinessRepository> { BusinessRepositoryImpl(get()) }
        single<BookingRepository> { BookingRepositoryImpl(get()) }
        single<FavoriteRepository> { FavoriteRepositoryImpl(get()) }
        single<ManagementRepository> { ManagementRepositoryImpl(get(), get()) }
        single<PlatformRepository> { PlatformRepositoryImpl(get(), get()) }
    }
}

val useCaseModule = module {
    // Auth
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { GetMeUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ChangePasswordUseCase(get()) }
    factory { ChangeEmailUseCase(get()) }
    factory { UploadAvatarUseCase(get()) }
    factory { DeleteAvatarUseCase(get()) }
    factory { GetMyBusinessRequestUseCase(get()) }
    factory { SubmitBusinessRequestUseCase(get()) }

    // Customer - discovery
    factory { ListBusinessesUseCase(get()) }
    factory { GetBusinessUseCase(get()) }
    factory { ListBusinessReviewsUseCase(get()) }
    factory { ListBranchesUseCase(get()) }
    factory { ListBranchServicesUseCase(get()) }
    factory { ListProvidersUseCase(get()) }
    factory { GetCategoriesUseCase(get()) }
    factory { GetCatalogUseCase(get()) }

    // Customer - booking
    factory { GetSlotsUseCase(get()) }
    factory { CreateAppointmentUseCase(get()) }
    factory { ListAppointmentsUseCase(get()) }
    factory { CancelAppointmentUseCase(get()) }
    factory { CreateReviewUseCase(get()) }

    // Customer - favorites
    factory { ListFavoritesUseCase(get()) }
    factory { ToggleFavoriteUseCase(get()) }

    // Admin
    factory { AdminDashboardUseCase(get()) }
    factory { AdminAnalyticsUseCase(get()) }
    factory { AdminContextUseCase(get()) }
    factory { AdminListBusinessesUseCase(get()) }
    factory { AdminListBranchesUseCase(get()) }
    factory { AdminCreateBranchUseCase(get()) }
    factory { AdminUpdateBranchUseCase(get()) }
    factory { AdminToggleBranchActiveUseCase(get()) }
    factory { AdminDeleteBranchUseCase(get()) }
    factory { AdminApproveBranchUseCase(get()) }
    factory { AdminListResourcesUseCase(get()) }
    factory { AdminListMembershipsUseCase(get()) }
    factory { AdminCreateMembershipUseCase(get()) }
    factory { AdminDeleteMembershipUseCase(get()) }
    factory { AdminListTeamUseCase(get()) }
    factory { AdminCreateTeamMemberUseCase(get()) }
    factory { AdminListCustomersUseCase(get()) }
    factory { AdminCreateCustomerUseCase(get()) }
    factory { AdminTogglePersonActiveUseCase(get()) }
    factory { AdminListServicesUseCase(get()) }
    factory { AdminCreateServiceUseCase(get()) }
    factory { AdminUpdateServiceUseCase(get()) }
    factory { AdminToggleServiceActiveUseCase(get()) }
    factory { AdminListAppointmentsUseCase(get()) }
    factory { AdminSetAppointmentStatusUseCase(get()) }
    factory { AdminListReviewsUseCase(get()) }
    factory { AdminModerateReviewUseCase(get()) }
    factory { AdminListAvailabilityUseCase(get()) }
    factory { AdminCreateAvailabilityUseCase(get()) }
    factory { AdminUpdateAvailabilityUseCase(get()) }
    factory { AdminDeleteAvailabilityUseCase(get()) }
    factory { AdminListResourceServicesUseCase(get()) }
    factory { AdminListCustomerReviewsUseCase(get()) }
    factory { AdminListPendingCustomerReviewsUseCase(get()) }
    factory { AdminCreateCustomerReviewUseCase(get()) }

    // Platform
    factory { PlatformListBusinessesUseCase(get()) }
    factory { PlatformCreateBusinessUseCase(get()) }
    factory { PlatformUpdateBusinessUseCase(get()) }
    factory { PlatformToggleBusinessActiveUseCase(get()) }
    factory { PlatformDeleteBusinessUseCase(get()) }
    factory { PlatformListCatalogUseCase(get()) }
    factory { PlatformCreateCatalogUseCase(get()) }
    factory { PlatformUpdateCatalogUseCase(get()) }
    factory { PlatformDeleteCatalogUseCase(get()) }
    factory { PlatformListCategoriesUseCase(get()) }
    factory { PlatformCreateCategoryUseCase(get()) }
    factory { PlatformUpdateCategoryUseCase(get()) }
    factory { PlatformDeleteCategoryUseCase(get()) }
    factory { PlatformListUsersUseCase(get()) }
    factory { PlatformCreateUserUseCase(get()) }
    factory { PlatformUpdateUserUseCase(get()) }
    factory { PlatformSetUserRolesUseCase(get()) }
    factory { PlatformToggleUserActiveUseCase(get()) }
    factory { PlatformResetPasswordUseCase(get()) }
    factory { PlatformListBusinessRequestsUseCase(get()) }
    factory { PlatformApproveBusinessRequestUseCase(get()) }
    factory { PlatformRejectBusinessRequestUseCase(get()) }
    factory { PlatformApproveBranchUseCase(get()) }
}

val viewModelModule = module {
    viewModel { AuthViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { BusinessDetailViewModel(get(), get(), get()) }
    viewModel { BookingViewModel(get(), get(), get(), get(), get()) }
    viewModel { AppointmentsViewModel(get(), get(), get()) }
    viewModel { FavoritesViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Admin
    viewModel { AdminDashboardViewModel(get(), get(), get()) }
    viewModel { AdminAnalyticsViewModel(get(), get()) }
    viewModel { AdminCalendarViewModel(get(), get()) }
    viewModel { AdminBranchesViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AdminReviewsViewModel(get(), get()) }
    viewModel { AdminCustomersViewModel(get(), get(), get()) }
    viewModel { AdminTeamViewModel(get(), get(), get(), get()) }
    viewModel { AdminServicesViewModel(get(), get(), get(), get()) }
    viewModel { AdminAvailabilityViewModel(get(), get(), get(), get(), get()) }

    // Platform
    viewModel { PlatformBusinessesViewModel(get(), get(), get(), get(), get()) }
    viewModel { PlatformCatalogViewModel(get(), get(), get(), get()) }
    viewModel { PlatformCategoriesViewModel(get(), get(), get(), get()) }
    viewModel { PlatformAccessViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PlatformRequestsViewModel(get(), get(), get()) }
}

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
        }
    }
    single {
        HttpClient(Android) {
            expectSuccess = false
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.d(App.API_TAG, message)
                        }
                    }
                    level = LogLevel.INFO
                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
                    filter { request -> request.url.toString().startsWith(BeNobatApiConfig.apiBaseUrl) }
                }
            }
        }
    }
    single {
        val tokenManager = get<TokenManager>()
        BeNobatApiClient(
            client = get(),
            baseUrl = BeNobatApiConfig.apiBaseUrl,
            tokenProvider = { tokenManager.userToken.first() },
        )
    }
}
