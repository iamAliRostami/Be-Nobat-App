/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "listRule": "@request.auth.id != \"\" &&\n(\n    client_user_id = @request.auth.id ||\n    branch_id.business_id.owner_user_id = @request.auth.id ||\n    (\n        @collection.appointment_services.appointment_id ?= id &&\n        @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n    )\n)",
    "viewRule": "@request.auth.id != \"\" &&\n(\n    client_user_id = @request.auth.id ||\n    branch_id.business_id.owner_user_id = @request.auth.id ||\n    (\n        @collection.appointment_services.appointment_id ?= id &&\n        @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n    )\n)"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "listRule": "client_user_id = @request.auth.id ||\nbranch_id.business_id.owner_user_id = @request.auth.id ||\n(\n    @collection.appointment_services.appointment_id ?= id &&\n    @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n)",
    "viewRule": "client_user_id = @request.auth.id ||\nbranch_id.business_id.owner_user_id = @request.auth.id ||\n(\n    @collection.appointment_services.appointment_id ?= id &&\n    @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n)"
  }, collection)

  return app.save(collection)
})
