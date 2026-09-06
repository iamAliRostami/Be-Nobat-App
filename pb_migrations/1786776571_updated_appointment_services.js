/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_1970990732",
    "help": "",
    "hidden": false,
    "id": "relation3853857785",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "appointment_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_662238796",
    "help": "",
    "hidden": false,
    "id": "relation3335097862",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "service_assignment_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(3, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3402113753",
    "max": 0,
    "min": 1,
    "name": "price",
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
    "id": "date2502384312",
    "max": "",
    "min": "",
    "name": "start_at",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "date"
  }))

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "number2254405824",
    "max": null,
    "min": 5,
    "name": "duration",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_1970990732",
    "help": "",
    "hidden": false,
    "id": "relation3853857785",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "appointment_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
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

  // update field
  collection.fields.addAt(3, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3402113753",
    "max": 0,
    "min": 0,
    "name": "price",
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
    "id": "date2502384312",
    "max": "",
    "min": "",
    "name": "start_at",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "date"
  }))

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "number2254405824",
    "max": null,
    "min": 0,
    "name": "duration",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  return app.save(collection)
})
