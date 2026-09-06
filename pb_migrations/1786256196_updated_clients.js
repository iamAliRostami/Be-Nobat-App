/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT client.id, client.name, client.mobile_ FROM users AS client WHERE client.access LIKE '%aq5021v2io2zi9g%'"
  }, collection)

  // remove field
  collection.fields.removeById("_clone_NwG4")

  // remove field
  collection.fields.removeById("_clone_f5Or")

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_BOsl",
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
  collection.fields.addAt(2, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_Qoc1",
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

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT client.id, client.name, client.mobile FROM users AS client WHERE client.access LIKE '%aq5021v2io2zi9g%'"
  }, collection)

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "_clone_NwG4",
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
  collection.fields.addAt(2, new Field({
    "help": "",
    "hidden": false,
    "id": "_clone_f5Or",
    "max": null,
    "min": -1,
    "name": "mobile",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  // remove field
  collection.fields.removeById("_clone_BOsl")

  // remove field
  collection.fields.removeById("_clone_Qoc1")

  return app.save(collection)
})
