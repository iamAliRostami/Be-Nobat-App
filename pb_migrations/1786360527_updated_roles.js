/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // add field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "select1997877400",
    "maxSelect": 0,
    "name": "code",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "platform_admin",
      "manager",
      "staff"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // remove field
  collection.fields.removeById("select1997877400")

  return app.save(collection)
})
