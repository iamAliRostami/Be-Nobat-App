/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "listRule": "status = \"active\" &&\ndeleted_at = \"\"",
    "updateRule": null,
    "viewRule": "status = \"active\" &&\ndeleted_at = \"\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "listRule": null,
    "updateRule": "user_id = @request.auth.id",
    "viewRule": null
  }, collection)

  return app.save(collection)
})
