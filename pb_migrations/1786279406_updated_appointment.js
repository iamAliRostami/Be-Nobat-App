/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // remove field
  collection.fields.removeById("relation3982272998")

  // add field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "date16528305",
    "max": "",
    "min": "",
    "name": "end",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "date"
  }))

  // add field
  collection.fields.addAt(7, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text163230955",
    "max": 0,
    "min": 0,
    "name": "total_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(8, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3772865661",
    "max": 0,
    "min": 0,
    "name": "discount_amount",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(9, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3419435337",
    "max": 0,
    "min": 0,
    "name": "final_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "date2675529103",
    "max": "",
    "min": "",
    "name": "start",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "date"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // add field
  collection.fields.addAt(4, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_863811952",
    "help": "",
    "hidden": false,
    "id": "relation3982272998",
    "maxSelect": 10,
    "minSelect": 0,
    "name": "service_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // remove field
  collection.fields.removeById("date16528305")

  // remove field
  collection.fields.removeById("text163230955")

  // remove field
  collection.fields.removeById("text3772865661")

  // remove field
  collection.fields.removeById("text3419435337")

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "date2675529103",
    "max": "",
    "min": "",
    "name": "start",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "date"
  }))

  return app.save(collection)
})
