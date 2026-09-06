/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2190040129")

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select3343321666",
    "maxSelect": 0,
    "name": "gender",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "male",
      "female",
      "other"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2190040129")

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select3343321666",
    "maxSelect": 0,
    "name": "gender",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "man",
      "woman",
      "other"
    ]
  }))

  return app.save(collection)
})
