/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // add field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "select11490771",
    "maxSelect": 0,
    "name": "scope",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "system",
      "branch"
    ]
  }))

  // add field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select2063623452",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "active",
      "disable"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // remove field
  collection.fields.removeById("select11490771")

  // remove field
  collection.fields.removeById("select2063623452")

  return app.save(collection)
})
