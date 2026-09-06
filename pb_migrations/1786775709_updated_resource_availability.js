/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_zlbfspz6yo` ON `resource_availability` (`resource_assignment_id`)",
      "CREATE INDEX `idx_4zpmhfovq4` ON `resource_availability` (`day_of_week`)"
    ]
  }, collection)

  // remove field
  collection.fields.removeById("date3596671328")

  // remove field
  collection.fields.removeById("date468475070")

  // add field
  collection.fields.addAt(3, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text3596671328",
    "max": 0,
    "min": 0,
    "name": "open_time",
    "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // add field
  collection.fields.addAt(5, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text468475070",
    "max": 0,
    "min": 0,
    "name": "close_time",
    "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)$",
    "presentable": false,
    "primaryKey": false,
    "required": true,
    "system": false,
    "type": "text"
  }))

  // update field
  collection.fields.addAt(1, new Field({
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
  collection.fields.addAt(2, new Field({
    "help": "",
    "hidden": false,
    "id": "number111645041",
    "max": 7,
    "min": 1,
    "name": "day_of_week",
    "onlyInt": false,
    "presentable": false,
    "required": true,
    "system": false,
    "type": "number"
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select2063623452",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": true,
    "system": false,
    "type": "select",
    "values": [
      "active",
      "inactive"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  // add field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "date3596671328",
    "max": "",
    "min": "",
    "name": "open_time",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "date"
  }))

  // add field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "date468475070",
    "max": "",
    "min": "",
    "name": "close_time",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "date"
  }))

  // remove field
  collection.fields.removeById("text3596671328")

  // remove field
  collection.fields.removeById("text468475070")

  // update field
  collection.fields.addAt(1, new Field({
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
  collection.fields.addAt(2, new Field({
    "help": "",
    "hidden": false,
    "id": "number111645041",
    "max": null,
    "min": null,
    "name": "day_of_week",
    "onlyInt": false,
    "presentable": false,
    "required": false,
    "system": false,
    "type": "number"
  }))

  // update field
  collection.fields.addAt(5, new Field({
    "help": "",
    "hidden": false,
    "id": "select2063623452",
    "maxSelect": 0,
    "name": "status",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "active",
      "inactive"
    ]
  }))

  return app.save(collection)
})
