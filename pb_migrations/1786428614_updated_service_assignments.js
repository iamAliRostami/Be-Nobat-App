/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.branch_services.id ?= @request.body.branch_service_id &&\n@collection.branch_services.branch_id.business_id.owner_user_id ?= @request.auth.id",
    "listRule": "status = \"active\" &&\nbranch_service_id.status = \"active\" &&\nresource_assignment_id.status = \"active\"",
    "updateRule": "branch_service_id.branch_id.business_id.owner_user_id = @request.auth.id &&\n@request.body.branch_service_id:changed = false &&\n@request.body.resource_assignment_id:changed = false",
    "viewRule": "status = \"active\" &&\nbranch_service_id.status = \"active\" &&\nresource_assignment_id.status = \"active\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "createRule": null,
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
