/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2536409462")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_ettb6n04yd` ON `branches` (`code`)",
      "CREATE INDEX `idx_mdq2e0vdjb` ON `branches` (`business_id`)",
      "CREATE INDEX `idx_lkybj0vefu` ON `branches` (`status`)",
      "CREATE INDEX `idx_1zw4c8px9v` ON `branches` (\n  `business_id`,\n  `code`\n)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3548013948",
    "help": "",
    "hidden": false,
    "id": "relation2828907607",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "business_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text1579384326",
    "max": 0,
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
  const collection = app.findCollectionByNameOrId("pbc_2536409462")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_ettb6n04yd` ON `branches` (`code`)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_3548013948",
    "help": "",
    "hidden": false,
    "id": "relation2828907607",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "business_id",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text1579384326",
    "max": 0,
    "min": 0,
    "name": "name",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  return app.save(collection)
})
