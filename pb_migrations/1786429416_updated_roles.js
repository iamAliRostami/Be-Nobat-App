/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.role_id.scope = \"system\"",
    "deleteRule": "@request.auth.role_id.scope = \"system\"",
    "updateRule": "@request.auth.role_id.scope = \"system\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "createRule": null,
    "deleteRule": null,
    "updateRule": null
  }, collection)

  return app.save(collection)
})
