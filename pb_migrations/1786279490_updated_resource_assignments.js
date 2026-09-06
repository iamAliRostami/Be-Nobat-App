/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_958181783")

  // add field
  collection.fields.addAt(4, new Field({
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

  // add field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "date2502384312",
    "max": "",
    "min": "",
    "name": "start_date",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "date"
  }))

  // add field
  collection.fields.addAt(6, new Field({
    "help": "",
    "hidden": false,
    "id": "date2220669758",
    "max": "",
    "min": "",
    "name": "end_date",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "date"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_958181783")

  // remove field
  collection.fields.removeById("relation3853857785")

  // remove field
  collection.fields.removeById("date2502384312")

  // remove field
  collection.fields.removeById("date2220669758")

  return app.save(collection)
})
