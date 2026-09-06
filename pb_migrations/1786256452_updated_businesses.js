/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3548013948")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_p87wrzvm2l` ON `businesses` (\n  `owner_user_id`,\n  `phone`\n)"
    ]
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3548013948")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  return app.save(collection)
})
