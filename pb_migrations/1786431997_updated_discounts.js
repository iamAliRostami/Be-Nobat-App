/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  // update collection data
  unmarshal({
    "updateRule": "business_id.owner_user_id = @request.auth.id &&\n@request.body.business_id:changed = false &&\n@request.body.used_count:changed = false"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  // update collection data
  unmarshal({
    "updateRule": "@request.auth.id != \"\" &&\n@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id"
  }, collection)

  return app.save(collection)
})
