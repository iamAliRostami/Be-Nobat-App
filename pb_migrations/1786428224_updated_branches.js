/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2536409462")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id",
    "deleteRule": null,
    "listRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbusiness_id.status = \"active\" &&\nbusiness_id.deleted_at = \"\"",
    "updateRule": "business_id.owner_user_id = @request.auth.id &&\n@request.body.business_id:changed = false &&\n@request.body.deleted_at:isset = false &&\n@request.body.deleted_by:isset = false",
    "viewRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbusiness_id.status = \"active\" &&\nbusiness_id.deleted_at = \"\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2536409462")

  // update collection data
  unmarshal({
    "createRule": "business_id.owner_user_id = @request.auth.id",
    "deleteRule": "business_id.owner_user_id = @request.auth.id",
    "listRule": null,
    "updateRule": "business_id.owner_user_id = @request.auth.id",
    "viewRule": null
  }, collection)

  return app.save(collection)
})
