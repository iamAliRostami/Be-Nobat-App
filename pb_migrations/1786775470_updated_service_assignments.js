/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE UNIQUE INDEX `idx_m9do6bx0ec` ON `service_assignments` (\n  `branch_service_id`,\n  `resource_assignment_id`\n)",
      "CREATE INDEX `idx_dzl3fpgb9y` ON `service_assignments` (`status`)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_4208842040",
    "help": "",
    "hidden": false,
    "id": "relation4068778168",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_service_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
    "cascadeDelete": true,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation2558469182",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(3, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3177487875",
    "max": 0,
    "min": 1,
    "name": "price_overide",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "number1514908556",
    "max": null,
    "min": 5,
    "name": "duration_override",
    "onlyInt": false,
    "presentable": false,
    "required": false,
    "system": false,
    "type": "number"
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  // update field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_4208842040",
    "help": "",
    "hidden": false,
    "id": "relation4068778168",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "branch_service_id",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "relation"
  }))

  // update field
  collection.fields.addAt(2, new Field({
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

  // update field
  collection.fields.addAt(3, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3177487875",
    "max": 0,
    "min": 0,
    "name": "price_overide",
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "number1514908556",
    "max": null,
    "min": null,
    "name": "duration_override",
    "onlyInt": false,
    "presentable": false,
    "required": false,
    "system": false,
    "type": "number"
  }))

  return app.save(collection)
})
