/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("_pb_users_auth_")

  // update collection data
  unmarshal({
    "createRule": "@request.body.status = \"active\" &&\n@request.body.verified:isset = false",
    "deleteRule": null
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("_pb_users_auth_")

  // update collection data
  unmarshal({
    "createRule": "@request.body.status:isset = false &&\n@request.body.verified:isset = false",
    "deleteRule": ""
  }, collection)

  return app.save(collection)
})
