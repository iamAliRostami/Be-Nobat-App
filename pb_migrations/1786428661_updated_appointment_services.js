/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@collection.appointment.id ?= @request.body.appointment_id &&\n@collection.appointment.client_user_id ?= @request.auth.id",
    "listRule": "appointment_id.client_user_id = @request.auth.id ||\nappointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nservice_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id",
    "viewRule": "appointment_id.client_user_id = @request.auth.id ||\nappointment_id.branch_id.business_id.owner_user_id = @request.auth.id ||\nservice_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update collection data
  unmarshal({
    "createRule": null,
    "listRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
