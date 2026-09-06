/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "name": "client",
    "viewQuery": "SELECT client.mobile, client.name, client.id FROM users AS client WHERE client.access IN ('aq5021v2io2zi9g')"
  }, collection)

  // remove field
  collection.fields.removeById("_clone_bDnL")

  // remove field
  collection.fields.removeById("_clone_JO3X")

  // add field
  collection.fields.addAt(0, new Field({
    "hidden": false,
    "id": "_clone_RA5h",
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
    "id": "_clone_R80W",
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
  const collection = app.findCollectionByNameOrId("pbc_3325881071")

  // update collection data
  unmarshal({
    "name": "clienr",
    "viewQuery": "SELECT client.mobile, client.name, client.id FROM users AS client WHERE access IN ('aq5021v2io2zi9g')"
  }, collection)

  // add field
  collection.fields.addAt(0, new Field({
    "hidden": false,
    "id": "_clone_bDnL",
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
    "id": "_clone_JO3X",
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
  collection.fields.removeById("_clone_RA5h")

  // remove field
  collection.fields.removeById("_clone_R80W")

  return app.save(collection)
})
