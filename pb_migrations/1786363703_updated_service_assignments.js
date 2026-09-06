/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // add field
  collection.fields.addAt(6, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_4208842040",
    "help": "",
    "hidden": false,
    "id": "relation4068778168",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_service_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // remove field
  collection.fields.removeById("relation4068778168")

  return app.save(collection)
})
