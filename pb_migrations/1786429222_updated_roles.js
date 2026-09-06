/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_iugioe1k4z` ON `roles` (`code`)",
      "CREATE INDEX `idx_ddxyxujkui` ON `roles` (`status`)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
