/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "updateRule": "user_id = @request.auth.id"
  }, collection)

  // remove field
  collection.fields.removeById("relation3705064521")

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2337082678")

  // update collection data
  unmarshal({
    "updateRule": "user_id = @request.auth.id || branch_id.business_id.owner_user_id = @request.auth.id"
  }, collection)

  // add field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_2536409462",
    "help": "",
    "hidden": false,
    "id": "relation3705064521",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  return app.save(collection)
})
