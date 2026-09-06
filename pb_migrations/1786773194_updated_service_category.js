/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_atimr16j2d` ON `service_category` (`business_id`)",
      "CREATE INDEX `idx_n8ttunk5kw` ON `service_category` (`parent_id`)",
      "CREATE INDEX `idx_axs9v726ek` ON `service_category` (`sort_order`)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
