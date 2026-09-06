/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_4163081445")

  // update collection data
  unmarshal({
    "createRule": "@request.auth.id != \"\" &&\n@request.body.user_id = @request.auth.id &&\n@collection.appointment_services.id ?= @request.body.appointment_service_id &&\n@collection.appointment_services.appointment_id.client_user_id ?= @request.auth.id &&\n@collection.appointment_services.appointment_id.status ?= \"completed\"",
    "indexes": [
      "CREATE INDEX `idx_bxj2mlrvo4` ON `reviews` (\n  `user_id`,\n  `appointment_service_id`\n)"
    ],
    "listRule": "",
    "updateRule": "user_id = @request.auth.id &&\n@request.body.user_id:changed = false &&\n@request.body.appointment_service_id:changed = false",
    "viewRule": ""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_4163081445")

  // update collection data
  unmarshal({
    "createRule": null,
    "indexes": [],
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
