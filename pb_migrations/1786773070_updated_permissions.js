/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3709660955")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE UNIQUE INDEX `idx_20wb02yvhl` ON `permissions` (`code`)",
      "CREATE INDEX `idx_hyzob64def` ON `permissions` (`scope`)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3709660955")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
