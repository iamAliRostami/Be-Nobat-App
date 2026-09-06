/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_upullajf1c` ON `appointment_services` (`appointment_id`)",
      "CREATE UNIQUE INDEX `idx_jle9429979` ON `appointment_services` (\n  `appointment_id`,\n  `order`\n)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
