/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_958181783")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.branches.id ?= @request.body.branch_id &&\n@collection.branches.business_id.owner_user_id ?= @request.auth.id",
    "listRule": "status = \"active\" &&\nbranch_id.status = \"active\" &&\nresource_id.status = \"active\"",
    "updateRule": "branch_id.business_id.owner_user_id = @request.auth.id &&\n@request.body.branch_id:changed = false &&\n@request.body.resource_id:changed = false",
    "viewRule": "status = \"active\" &&\nbranch_id.status = \"active\" &&\nresource_id.status = \"active\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_958181783")

  // update collection data
  unmarshal({
    "createRule": null,
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
