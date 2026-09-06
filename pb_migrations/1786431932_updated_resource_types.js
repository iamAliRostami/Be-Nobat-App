/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1432389269")

  // update collection data
  unmarshal({
    "createRule": null,
    "deleteRule": null,
    "updateRule": null
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1432389269")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.role.admin = true || @request.auth.role.owner = true",
    "deleteRule": "@request.auth.role.admin = true",
    "updateRule": "@request.auth.role.admin = true || @request.auth.role.owner = true"
  }, collection)

  return app.save(collection)
})
