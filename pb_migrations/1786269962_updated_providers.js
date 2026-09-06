/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2443499360")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT provider.mobile, provider.name, provider.id FROM users AS provider "
  }, collection)

  // remove field
  collection.fields.removeById("_clone_D555")

  // remove field
  collection.fields.removeById("_clone_6J6G")

  // remove field
  collection.fields.removeById("_clone_Te0A")

  // add field
  collection.fields.addAt(0, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_U0rW",
    "max": 11,
    "min": 11,
    "name": "mobile",
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
    "id": "_clone_wZNt",
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

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2443499360")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT provider.mobile, provider.name, provider.id, provider.access FROM users AS provider WHERE provider.access like '%a00dg6c94sm0kcj%'"
  }, collection)

  // add field
  collection.fields.addAt(0, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_D555",
    "max": 11,
    "min": 11,
    "name": "mobile",
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
    "id": "_clone_6J6G",
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
    "id": "_clone_Te0A",
    "maxSelect": 999,
    "minSelect": 0,
    "name": "access",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // remove field
  collection.fields.removeById("_clone_U0rW")

  // remove field
  collection.fields.removeById("_clone_wZNt")

  return app.save(collection)
})
