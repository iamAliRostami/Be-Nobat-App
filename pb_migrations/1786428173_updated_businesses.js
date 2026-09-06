/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3548013948")

  // update collection data
  unmarshal({
    "createRule": null,
    "deleteRule": null,
    "listRule": "status = \"active\" &&\ndeleted_at = \"\"",
    "updateRule": "owner_user_id = @request.auth.id &&\n@request.body.owner_user_id:changed = false &&\n@request.body.deleted_at:isset = false &&\n@request.body.deleted_by:isset = false",
    "viewRule": "status = \"active\" &&\ndeleted_at = \"\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3548013948")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\"",
    "deleteRule": "owner_user_id = @request.auth.id",
    "listRule": "status = 'active'",
    "updateRule": "owner_user_id = @request.auth.id",
    "viewRule": null
  }, collection)

  return app.save(collection)
})
