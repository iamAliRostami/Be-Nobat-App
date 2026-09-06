/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // update collection data
  unmarshal({
    "listRule": "@request.auth.id != \"\" &&\n(\n    branch_id.business_id.owner_user_id = @request.auth.id ||\n    resource_assignment_id.resource_id.user_id = @request.auth.id\n)",
    "viewRule": "@request.auth.id != \"\" &&\n(\n    branch_id.business_id.owner_user_id = @request.auth.id ||\n    resource_assignment_id.resource_id.user_id = @request.auth.id\n)"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // update collection data
  unmarshal({
    "listRule": "branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id",
    "viewRule": "branch_id.business_id.owner_user_id = @request.auth.id ||\nresource_assignment_id.resource_id.user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
})
