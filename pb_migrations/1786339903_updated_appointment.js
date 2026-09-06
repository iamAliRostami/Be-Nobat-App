/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": true,
    "id": "relation2558469182",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation2558469182",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
