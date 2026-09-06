/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_662238796",
    "help": "",
    "hidden": false,
    "id": "relation3335097862",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "service_assignment_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_662238796",
    "help": "",
    "hidden": false,
    "id": "relation3335097862",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "service_provider_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
