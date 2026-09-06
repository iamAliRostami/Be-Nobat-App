/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id",
    "listRule": "business_id.owner_user_id = @request.auth.id",
    "updateRule": "@request.auth.id != \"\" &&\n@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id",
    "viewRule": "business_id.owner_user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  // update collection data
  unmarshal({
    "createRule": null,
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
