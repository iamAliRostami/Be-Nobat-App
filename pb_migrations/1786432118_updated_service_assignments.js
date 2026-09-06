/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "listRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbranch_service_id.status = \"active\" &&\nbranch_service_id.deleted_at = \"\" &&\nbranch_service_id.branch_id.status = \"active\" &&\nbranch_service_id.branch_id.deleted_at = \"\" &&\nbranch_service_id.service_id.status = \"active\" &&\nbranch_service_id.service_id.deleted_at = \"\" &&\nresource_assignment_id.status = \"active\" &&\nresource_assignment_id.deleted_at = \"\" &&\nresource_assignment_id.resource_id.status = \"active\" &&\nresource_assignment_id.resource_id.deleted_at = \"\"",
    "viewRule": "status = \"active\" &&\ndeleted_at = \"\" &&\nbranch_service_id.status = \"active\" &&\nbranch_service_id.deleted_at = \"\" &&\nbranch_service_id.branch_id.status = \"active\" &&\nbranch_service_id.branch_id.deleted_at = \"\" &&\nbranch_service_id.service_id.status = \"active\" &&\nbranch_service_id.service_id.deleted_at = \"\" &&\nresource_assignment_id.status = \"active\" &&\nresource_assignment_id.deleted_at = \"\" &&\nresource_assignment_id.resource_id.status = \"active\" &&\nresource_assignment_id.resource_id.deleted_at = \"\""
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "listRule": "status = \"active\" &&\nbranch_service_id.status = \"active\" &&\nresource_assignment_id.status = \"active\"",
    "viewRule": "status = \"active\" &&\nbranch_service_id.status = \"active\" &&\nresource_assignment_id.status = \"active\""
  }, collection)

  return app.save(collection)
})
