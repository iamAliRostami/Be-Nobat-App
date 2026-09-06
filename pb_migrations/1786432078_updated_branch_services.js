/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_4208842040")

  // update collection data
  unmarshal({
    "listRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbranch_id.status = \"active\" &&\nbranch_id.deleted_at = \"\" &&\nbranch_id.business_id.status = \"active\" &&\nbranch_id.business_id.deleted_at = \"\" &&\nservice_id.status = \"active\" &&\nservice_id.deleted_at = \"\"",
    "viewRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbranch_id.status = \"active\" &&\nbranch_id.deleted_at = \"\" &&\nbranch_id.business_id.status = \"active\" &&\nbranch_id.business_id.deleted_at = \"\" &&\nservice_id.status = \"active\" &&\nservice_id.deleted_at = \"\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_4208842040")

  // update collection data
  unmarshal({
    "listRule": "status = \"active\" &&\nbranch_id.status = \"active\" &&\nbranch_id.business_id.status = \"active\" &&\nservice_id.status = \"active\"",
    "viewRule": "status = \"active\" &&\nbranch_id.status = \"active\" &&\nbranch_id.business_id.status = \"active\" &&\nservice_id.status = \"active\""
  }, collection)

  return app.save(collection)
})
