/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // add field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_2536409462",
    "help": "",
    "hidden": false,
    "id": "relation3705064521",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation3895443815",
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
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // remove field
  collection.fields.removeById("relation3705064521")

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation3895443815",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id_",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
