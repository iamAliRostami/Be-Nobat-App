/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_63pts1seb9` ON `appointment` (\n\n  `branch_id`\n)",
      "CREATE INDEX `idx_gm39xfibvg` ON `appointment` (`client_user_id`)",
      "CREATE INDEX `idx_nd3bgevwgz` ON `appointment` (`start`)",
      "CREATE INDEX `idx_hq3ny29wmu` ON `appointment` (`end`)",
      "CREATE INDEX `idx_l0yam79yab` ON `appointment` (\n  `branch_id`,\n  `start`\n)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": true,
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

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": true,
    "collectionId": "_pb_users_auth_",
    "help": "",
    "hidden": false,
    "id": "relation4115896296",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "client_user_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "select1281290230",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "pending",
      "confirmed",
      "in_progress",
      "completed",
      "cancelled",
      "no_show"
    ]
  }))

  // update field
  collection.fields.addAt(6, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text163230955",
    "max": 0,
    "min": 1,
    "name": "total_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(7, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3772865661",
    "max": 0,
    "min": 1,
    "name": "discount_amount",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(8, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3419435337",
    "max": 0,
    "min": 1,
    "name": "final_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_63pts1seb9` ON `appointment` (\n\n  `branch_id`\n)"
    ]
  }, collection)

  // update field
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
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
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

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "select1281290230",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "pending, confirmed, canceled, completed"
    ]
  }))

  // update field
  collection.fields.addAt(6, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text163230955",
    "max": 0,
    "min": 0,
    "name": "total_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(7, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3772865661",
    "max": 0,
    "min": 0,
    "name": "discount_amount",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(8, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3419435337",
    "max": 0,
    "min": 0,
    "name": "final_price",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  return app.save(collection)
})
