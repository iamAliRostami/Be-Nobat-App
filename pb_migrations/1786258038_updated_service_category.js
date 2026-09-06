/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // add field
  collection.fields.addAt(4, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3780508793",
    "help": "",
    "hidden": false,
    "id": "relation1920649840",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "parent_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3780508793")

  // remove field
  collection.fields.removeById("relation1920649840")

  return app.save(collection)
})
