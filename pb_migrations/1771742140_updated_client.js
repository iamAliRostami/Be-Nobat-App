/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT client.id, client.name, client.mobile FROM users AS client WHERE client.access LIKE '%aq5021v2io2zi9g%'"
  }, collection)

  // remove field
  collection.fields.removeById("_clone_btUo")

  // remove field
  collection.fields.removeById("_clone_98JE")

  // add field
  collection.fields.addAt(1, new Field({
    "autogeneratePattern": "",
    "hidden": false,
    "id": "_clone_sHZr",
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
    "hidden": false,
    "id": "_clone_aXeN",
    "max": null,
    "min": -1,
    "name": "mobile",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "viewQuery": "SELECT client.mobile, client.name, client.id FROM users AS client WHERE client.access LIKE '%aq5021v2io2zi9g%'"
  }, collection)

  // add field
  collection.fields.addAt(0, new Field({
    "hidden": false,
    "id": "_clone_btUo",
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
    "hidden": false,
    "id": "_clone_98JE",
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

  // remove field
  collection.fields.removeById("_clone_sHZr")

  // remove field
  collection.fields.removeById("_clone_aXeN")

  return app.save(collection)
})
