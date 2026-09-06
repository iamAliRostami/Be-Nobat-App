/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "listRule": "@request.auth.id != \"\" &&\n(\n    resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\n    resource_assignment_id.resource_id.user_id = @request.auth.id\n)",
    "viewRule": "@request.auth.id != \"\" &&\n(\n    resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\n    resource_assignment_id.resource_id.user_id = @request.auth.id\n)"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "listRule": "resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id",
    "viewRule": "resource_assignment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
})
