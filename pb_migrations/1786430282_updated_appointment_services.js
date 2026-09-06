/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "listRule": "@request.auth.id != \"\" &&\n(\n    appointment_id.client_user_id = @request.auth.id ||\n    appointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\n    service_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id\n)",
    "viewRule": "@request.auth.id != \"\" &&\n(\n    appointment_id.client_user_id = @request.auth.id ||\n    appointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\n    service_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id\n)"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "listRule": "appointment_id.client_user_id = @request.auth.id ||\nappointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nservice_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id",
    "viewRule": "appointment_id.client_user_id = @request.auth.id ||\nappointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nservice_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
})
