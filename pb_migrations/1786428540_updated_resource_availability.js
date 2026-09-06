/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.resource_assignments.id ?= @request.body.resource_assignment_id &&\n@collection.resource_assignments.branch_id.business_id.owner_user_id ?= @request.auth.id",
    "listRule": "resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id",
    "updateRule": "resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id &&\n@request.body.resource_assignment_id:changed = false",
    "viewRule": "resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "createRule": null,
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
