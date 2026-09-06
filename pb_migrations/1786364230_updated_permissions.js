/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3709660955")

  // remove field
  collection.fields.removeById("select1997877400")

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text1997877400",
    "max": 0,
    "min": 0,
    "name": "code",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3709660955")

  // add field
  collection.fields.addAt(1, new Field({
    "help": "",
    "hidden": false,
    "id": "select1997877400",
    "maxSelect": 0,
    "name": "code",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "platform_admin",
      "manager",
      "staff"
    ]
  }))

  // remove field
  collection.fields.removeById("text1997877400")

  return app.save(collection)
})
