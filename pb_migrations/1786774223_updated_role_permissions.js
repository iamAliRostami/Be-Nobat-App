/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_30747235")

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_830101018",
    "help": "",
    "hidden": false,
    "id": "relation3590529708",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "role_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_3709660955",
    "help": "",
    "hidden": false,
    "id": "relation4275637450",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "permission_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_30747235")

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_830101018",
    "help": "",
    "hidden": false,
    "id": "relation3590529708",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "role_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3709660955",
    "help": "",
    "hidden": false,
    "id": "relation4275637450",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "permission_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
