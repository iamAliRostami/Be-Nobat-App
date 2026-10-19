These four files belong to the previous PocketBase implementation and are
preserved here unchanged. This directory is not an Android source set.

Appointment.kt and Service.kt duplicate the active models in Booking.kt and
Public.kt. LoginUseCase.kt duplicates AuthUseCases.kt and expects the old
repository return type. PocketBaseConfig.kt refers to removed BuildConfig
fields and has no callers in the active application.

Do not add this directory to the main source set without migrating these files.
