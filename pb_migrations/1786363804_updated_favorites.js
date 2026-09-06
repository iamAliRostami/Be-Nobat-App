/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2151843437")

  // remove field
  collection.fields.removeById("relation3577594973")

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_2337082678",
    "help": "",
    "hidden": false,
    "id": "relation2301795621",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3548013948",
    "help": "",
    "hidden": false,
    "id": "relation2828907607",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "business_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2151843437")

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_1262204345",
    "help": "",
    "hidden": false,
    "id": "relation3577594973",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "appointment_services_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // remove field
  collection.fields.removeById("relation2301795621")

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3548013948",
    "help": "",
    "hidden": false,
    "id": "relation2828907607",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "business_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
