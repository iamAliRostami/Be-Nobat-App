/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "listRule": "",
    "updateRule": "",
    "viewRule": ""
  }, collection)

  // remove field
  collection.fields.removeById("relation173254826")

  // add field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_2536409462",
    "help": "",
    "hidden": false,
    "id": "relation3705064521",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation2558469182",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
  collection.fields.addAt(4, new Field({
    "cascadeDelete": false,
    "collectionId": "_pb_users_auth_",
    "help": "",
    "hidden": false,
    "id": "relation4115896296",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "client_user_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
  collection.fields.addAt(8, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text18589324",
    "max": 0,
    "min": 0,
    "name": "notes",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "listRule": "client_id = @request.auth.id || provider_id = @request.auth.id",
    "updateRule": "client_id = @request.auth.id || provider_id = @request.auth.id",
    "viewRule": "client_id = @request.auth.id || provider_id = @request.auth.id"
  }, collection)

  // add field
  collection.fields.addAt(3, new Field({
    "cascadeDelete": false,
    "collectionId": "_pb_users_auth_",
    "help": "",
    "hidden": false,
    "id": "relation173254826",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "provider_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // remove field
  collection.fields.removeById("relation3705064521")

  // remove field
  collection.fields.removeById("relation2558469182")

  // remove field
  collection.fields.removeById("relation4115896296")

  // remove field
  collection.fields.removeById("text18589324")

  return app.save(collection)
})
