/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // remove field
  collection.fields.removeById("relation173254826")

  // add field
  collection.fields.addAt(1, new Field({
    "cascadeDelete": false,
    "collectionId": "pbc_958181783",
    "help": "",
    "hidden": false,
    "id": "relation3895443815",
    "maxSelect": 0,
    "minSelect": 0,
    "name": "resource_assignment_id_",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "relation"
  }))

  // add field
  collection.fields.addAt(3, new Field({
    "help": "",
    "hidden": false,
    "id": "select3059782130",
    "maxSelect": 0,
    "name": "effect",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "available",
      "unavailable"
    ]
  }))

  // add field
  collection.fields.addAt(4, new Field({
    "help": "",
    "hidden": false,
    "id": "select2363381545",
    "maxSelect": 0,
    "name": "type",
    "presentable": false,
    "required": false,
    "system": false,
    "type": "select",
    "values": [
      "holiday",
      "leave",
      "maintenance",
      "special_hours",
      "manual_block",
      "other"
    ]
  }))

  // add field
  collection.fields.addAt(6, new Field({
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
      "enable",
      "disable"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // add field
  collection.fields.addAt(1, new Field({
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
  collection.fields.removeById("relation3895443815")

  // remove field
  collection.fields.removeById("select3059782130")

  // remove field
  collection.fields.removeById("select2363381545")

  // remove field
  collection.fields.removeById("select2063623452")

  return app.save(collection)
})
