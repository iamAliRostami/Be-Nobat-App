/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "listRule": "business_id.status = \"active\" &&\ndeleted_at = \"\" &&\nbusiness_id.deleted_at = \"\"\n",
    "viewRule": "business_id.status = \"active\" &&\ndeleted_at = \"\" &&\nbusiness_id.deleted_at = \"\"\n"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // update collection data
  unmarshal({
    "listRule": "business_id.status = \"active\" &&\ndeleted_at = \"\"",
    "viewRule": "business_id.status = \"active\" &&\ndeleted_at = \"\""
  }, collection)

  return app.save(collection)
})
