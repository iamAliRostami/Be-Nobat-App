/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // add field
  collection.fields.addAt(5, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_863811952",
    "help": "",
    "hidden": false,
    "id": "relation3982272998",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "service_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select1281290230",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "pending, confirmed, canceled, completed"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // remove field
  collection.fields.removeById("relation3982272998")

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select1281290230",
    "maxSelect": 0,
    "name": "Select",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "pending, confirmed, canceled, completed"
    ]
  }))

  return app.save(collection)
})
