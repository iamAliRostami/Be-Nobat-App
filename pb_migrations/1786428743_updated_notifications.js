/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2301922722")

  // update collection data
  unmarshal({
    "listRule": "user_id = @request.auth.id",
    "updateRule": "user_id = @request.auth.id &&\n@request.body.user_id:changed = false &&\n@request.body.title:changed = false &&\n@request.body.body:changed = false &&\n@request.body.type:changed = false &&\n@request.body.reference_id:changed = false &&\n@request.body.sent_at:changed = false",
    "viewRule": "user_id = @request.auth.id"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2301922722")

  // update collection data
  unmarshal({
    "listRule": null,
    "updateRule": null,
    "viewRule": null
  }, collection)

  return app.save(collection)
})
