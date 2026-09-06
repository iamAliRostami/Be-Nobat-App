/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_4294037838")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE UNIQUE INDEX `idx_x0ilsuqkv6` ON `user_system_roles` (\n  `user_id`,\n  `role_id`\n)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_4294037838")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
