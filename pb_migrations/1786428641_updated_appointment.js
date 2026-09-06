/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@request.body.client_user_id = @request.auth.id &&\n@collection.branches.id ?= @request.body.branch_id &&\n@collection.branches.status ?= \"active\"",
    "listRule": "client_user_id = @request.auth.id ||\nbranch_id.business_id.owner_user_id = @request.auth.id ||\n(\n    @collection.appointment_services.appointment_id ?= id &&\n    @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n)",
    "updateRule": "(\n    client_user_id = @request.auth.id &&\n    @request.body.client_user_id:changed = false &&\n    @request.body.branch_id:changed = false &&\n    @request.body.total_price:changed = false &&\n    @request.body.discount_amount:changed = false &&\n    @request.body.final_price:changed = false\n)\n||\nbranch_id.business_id.owner_user_id = @request.auth.id",
    "viewRule": "client_user_id = @request.auth.id ||\nbranch_id.business_id.owner_user_id = @request.auth.id ||\n(\n    @collection.appointment_services.appointment_id ?= id &&\n    @collection.appointment_services.service_assignment_id.resource_assignment_id.resource_id.user_id ?= @request.auth.id\n)"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\"",
    "listRule": "client_user_id = @request.auth.id || branch_id.business_id.owner_user_id = @request.auth.id",
    "updateRule": "",
    "viewRule": "client_user_id = @request.auth.id || branch_id.business_id.owner_user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
})
