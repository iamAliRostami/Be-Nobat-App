/// <reference path="../pb_data/types.d.ts" />

// قیود زیر قوانین دامنه‌ای هستند که صرفاً با hook کافی نیستند: دو درخواست
// هم‌زمان باید در نهایت توسط خود SQLite نیز نتوانند رکورد تکراری بسازند.
migrate((app) => {
  const reviews = app.findCollectionByNameOrId("reviews")
  reviews.indexes = reviews.indexes.filter((index) => index.indexOf("idx_bxj2mlrvo4") === -1)
  reviews.indexes.push(
    "CREATE UNIQUE INDEX `idx_review_appointment_service` ON `reviews` (`appointment_service_id`)"
  )
  app.save(reviews)

  const branchMembership = app.findCollectionByNameOrId("branch_membership")
  branchMembership.indexes = branchMembership.indexes.filter((index) => index.indexOf("idx_4oa3f7rmt5") === -1)
  branchMembership.indexes.push(
    "CREATE UNIQUE INDEX `idx_branch_membership_unique` ON `branch_membership` (`branch_id`, `user_id`)"
  )
  app.save(branchMembership)

  const rolePermissions = app.findCollectionByNameOrId("role_permissions")
  rolePermissions.indexes.push(
    "CREATE UNIQUE INDEX `idx_role_permission_unique` ON `role_permissions` (`role_id`, `permission_id`)"
  )
  app.save(rolePermissions)

  const availability = app.findCollectionByNameOrId("resource_availability")
  availability.indexes.push(
    "CREATE UNIQUE INDEX `idx_resource_availability_exact_unique` ON `resource_availability` (`resource_assignment_id`, `day_of_week`, `open_time`, `close_time`)"
  )
  app.save(availability)

  const exceptions = app.findCollectionByNameOrId("resource_exceptions")
  exceptions.indexes.push(
    "CREATE INDEX `idx_resource_exception_assignment_start` ON `resource_exceptions` (`resource_assignment_id`, `start_datetime`)"
  )
  exceptions.indexes.push(
    "CREATE INDEX `idx_resource_exception_branch_start` ON `resource_exceptions` (`branch_id`, `start_datetime`)"
  )
  return app.save(exceptions)
}, (app) => {
  const definitions = [
    ["reviews", "idx_review_appointment_service"],
    ["branch_membership", "idx_branch_membership_unique"],
    ["role_permissions", "idx_role_permission_unique"],
    ["resource_availability", "idx_resource_availability_exact_unique"],
    ["resource_exceptions", "idx_resource_exception_assignment_start"],
    ["resource_exceptions", "idx_resource_exception_branch_start"],
  ]

  for (let i = 0; i < definitions.length; i++) {
    const collection = app.findCollectionByNameOrId(definitions[i][0])
    const marker = "`" + definitions[i][1] + "`"
    collection.indexes = collection.indexes.filter((index) => index.indexOf(marker) === -1)
    app.save(collection)
  }

  // Restore the two non-unique indexes that existed before the migration.
  const reviews = app.findCollectionByNameOrId("reviews")
  reviews.indexes.push(
    "CREATE INDEX `idx_bxj2mlrvo4` ON `reviews` (`user_id`, `appointment_service_id`)"
  )
  app.save(reviews)

  const branchMembership = app.findCollectionByNameOrId("branch_membership")
  branchMembership.indexes.push(
    "CREATE INDEX `idx_4oa3f7rmt5` ON `branch_membership` (`branch_id`, `user_id`)"
  )
  app.save(branchMembership)
})
