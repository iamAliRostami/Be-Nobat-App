/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_4163081445")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_bxj2mlrvo4` ON `reviews` (\n  `user_id`,\n  `appointment_service_id`\n)",
      "CREATE INDEX `idx_r6cra0nd65` ON `reviews` (`user_id`)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_4163081445")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_bxj2mlrvo4` ON `reviews` (\n  `user_id`,\n  `appointment_service_id`\n)"
    ]
  }, collection)

  return app.save(collection)
})
