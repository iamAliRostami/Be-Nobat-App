/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("_pb_users_auth_")

  // update collection data
  unmarshal({
    "authRule": "status = \"active\"",
    "createRule": "@request.body.status:isset = false &&\n@request.body.verified:isset = false",
    "deleteRule": "",
    "updateRule": "id = @request.auth.id &&\n@request.body.status:changed = false &&\n@request.body.verified:changed = false"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("_pb_users_auth_")

  // update collection data
  unmarshal({
    "authRule": "",
    "createRule": "",
    "deleteRule": "id = @request.auth.id",
    "updateRule": "id = @request.auth.id"
  }, collection)

  return app.save(collection)
})
