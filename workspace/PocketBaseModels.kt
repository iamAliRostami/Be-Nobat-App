import kotlinx.serialization.Serializable

@Serializable
open class BaseModel {
    val id: String = ""
    val created: String = ""
    val updated: String = ""
}

@Serializable
data class UsersModel(

) : BaseModel()

@Serializable
data class AppointmentModel(

) : BaseModel()

@Serializable
data class AppointmentServicesModel(

) : BaseModel()

@Serializable
data class BranchMembershipModel(

) : BaseModel()

@Serializable
data class BranchServicesModel(

) : BaseModel()

@Serializable
data class BranchesModel(

) : BaseModel()

@Serializable
data class BusinessesModel(

) : BaseModel()

@Serializable
data class DiscountsModel(

) : BaseModel()

@Serializable
data class FavoritesModel(

) : BaseModel()

@Serializable
data class NotificationsModel(

) : BaseModel()

@Serializable
data class PermissionsModel(

) : BaseModel()

@Serializable
data class ReputationEventsModel(

) : BaseModel()

@Serializable
data class ResourceAssignmentsModel(

) : BaseModel()

@Serializable
data class ResourceAvailabilityModel(

) : BaseModel()

@Serializable
data class ResourceExceptionsModel(

) : BaseModel()

@Serializable
data class ResourceTypesModel(

) : BaseModel()

@Serializable
data class ResourcesModel(

) : BaseModel()

@Serializable
data class ReviewsModel(

) : BaseModel()

@Serializable
data class RolePermissionsModel(

) : BaseModel()

@Serializable
data class RolesModel(

) : BaseModel()

@Serializable
data class ServiceAssignmentsModel(

) : BaseModel()

@Serializable
data class ServiceCategoryModel(

) : BaseModel()

@Serializable
data class ServicesModel(

) : BaseModel()

@Serializable
data class UserProfilesModel(

) : BaseModel()

@Serializable
data class UserSystemRolesModel(

) : BaseModel()

