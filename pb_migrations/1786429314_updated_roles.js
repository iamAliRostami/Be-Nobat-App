/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_iugioe1k4z` ON `roles` (`code`)",
      "CREATE INDEX `idx_ddxyxujkui` ON `roles` (`status`)",
      "CREATE UNIQUE INDEX `idx_4yctlyigp6` ON `roles` (`title`)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "select11490771",
    "maxSelect": 0,
    "name": "scope",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "system",
      "branch",
      "business"
    ]
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text1997877400",
    "max": 0,
    "min": 0,
    "name": "code",
    "pattern": "^[a-z_]+$",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
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
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_iugioe1k4z` ON `roles` (`code`)",
      "CREATE INDEX `idx_ddxyxujkui` ON `roles` (`status`)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "select11490771",
    "maxSelect": 0,
    "name": "scope",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "system",
      "branch"
    ]
  }))

  // update field
  collection.fields.addAt(4, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": "text1997877400",
    "max": 0,
    "min": 0,
    "name": "code",
    "pattern": "",
    "presentable": false,
    "primaryKey": false,
    "required": false,
    "system": false,
    "type": "text"
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
      "disable"
    ]
  }))

  return app.save(collection)
})
