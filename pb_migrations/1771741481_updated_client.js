/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_4032264475")

  // update collection data
  unmarshal({
    "name": "client_provider"
  }, collection)

  // add field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "_pb_users_auth_",
    "hidden": false,
    "id": "relation3343123541",
    "maxSelect": 1,
    "minSelect": 0,
    "name": "client",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "_pb_users_auth_",
    "hidden": false,
    "id": "relation2462348188",
    "maxSelect": 1,
    "minSelect": 0,
    "name": "provider",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_4032264475")

  // update collection data
  unmarshal({
    "name": "client"
  }, collection)

  // remove field
  collection.fields.removeById("relation3343123541")

  // remove field
  collection.fields.removeById("relation2462348188")

  return app.save(collection)
})
