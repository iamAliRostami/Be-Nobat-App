/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2443499360")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT provider.mobile_, provider.name, provider.id, provider.access FROM users AS provider WHERE provider.access like '%a00dg6c94sm0kcj%'"
  }, collection)

  // remove field
  collection.fields.removeById("_clone_Opf8")

  // remove field
  collection.fields.removeById("_clone_8X7S")

  // remove field
  collection.fields.removeById("_clone_YoYu")

  // add field
  collection.fields.addAt(0, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_aLpT",
    "max": 11,
    "min": 11,
    "name": "mobile_",
    "pattern": "^09\\d{9}$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_Toje",
    "max": 255,
    "min": 0,
    "name": "name",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_830101018",
    "help": "",
    "hidden": false,
    "id": "_clone_Wqov",
    "maxSelect": 999,
    "minSelect": 0,
    "name": "access",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2443499360")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT provider.mobile, provider.name, provider.id, provider.access FROM users AS provider WHERE provider.access like '%a00dg6c94sm0kcj%'"
  }, collection)

  // add field
  collection.fields.addAt(0, new Field({
    "help": "",
    "hidden": false,
    "id": "_clone_Opf8",
    "max": null,
    "min": -1,
    "name": "mobile",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_8X7S",
    "max": 255,
    "min": 0,
    "name": "name",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_830101018",
    "help": "",
    "hidden": false,
    "id": "_clone_YoYu",
    "maxSelect": 999,
    "minSelect": 0,
    "name": "access",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // remove field
  collection.fields.removeById("_clone_aLpT")

  // remove field
  collection.fields.removeById("_clone_Toje")

  // remove field
  collection.fields.removeById("_clone_Wqov")

  return app.save(collection)
})
