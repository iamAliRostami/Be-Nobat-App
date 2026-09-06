/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // remove field
  collection.fields.removeById("date16528305")

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "_pb_users_auth_",
    "help": "",
    "hidden": false,
    "id": "relation173254826",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "provider_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
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
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // add field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "date16528305",
    "max": "",
    "min": "",
    "name": "end",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "date"
  }))

  // remove field
  collection.fields.removeById("relation173254826")

  // remove field
  collection.fields.removeById("select1281290230")

  return app.save(collection)
})
