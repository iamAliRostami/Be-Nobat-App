/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n((@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id)||\n(@request.auth.role.admin = true || @request.auth.role.owner = true || @request.auth.role.manager = true))\n",
    "updateRule": "business_id.owner_user_id = @request.auth.id &&\n((@request.body.business_id:changed = false &&\n@request.body.deleted_at:isset = false &&\n@request.body.deleted_by:isset = false)||\n(@request.auth.role.admin = true || @request.auth.role.owner = true || @request.auth.role.manager = true))"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.businesses.id ?= @request.body.business_id &&\n@collection.businesses.owner_user_id ?= @request.auth.id",
    "updateRule": "business_id.owner_user_id = @request.auth.id &&\n@request.body.business_id:changed = false &&\n@request.body.deleted_at:isset = false &&\n@request.body.deleted_by:isset = false"
  }, collection)

  return app.save(collection)
})
