/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_tzbajrntyi` ON `resources` (`user_id`)",
      "CREATE INDEX `idx_3lekt0oq4j` ON `resources` (`type`)",
      "CREATE INDEX `idx_afq2z0sntw` ON `resources` (`status`)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
