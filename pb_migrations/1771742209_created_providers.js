/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = new Collection({
    "createRule": null,
    "deleteRule": null,
    "fields": [
      {
        "hidden": false,
        "id": "_clone_Opf8",
        "max": null,
        "min": -1,
        "name": "mobile",
        "onlyInt": false,
        "presentable": false,
        "required": true,
        "system": false,
        "type": "number"
      },
      {
        "autogeneratePattern": "",
        "hidden": false,
        "id": "_clone_8X7S",
        "max": 255,
        "min": 0,
        "name": "name",
        "pattern": "",
        "presentable": false,
        "primaryKey": false,
        "required": true,
        "system": false,
        "type": "text"
      },
      {
        "autogeneratePattern": "",
        "hidden": false,
        "id": "text3208210256",
        "max": 0,
        "min": 0,
        "name": "id",
        "pattern": "^[a-z0-9]+$",
        "presentable": false,
        "primaryKey": true,
        "required": true,
        "system": true,
        "type": "text"
      },
      {
        "cascadeDelete": false,
        "collectionId": "pbc_830101018",
        "hidden": false,
        "id": "_clone_YoYu",
        "maxSelect": 999,
        "minSelect": 0,
        "name": "access",
        "presentable": false,
        "required": false,
        "system": false,
        "type": "relation"
      }
    ],
    "id": "pbc_2443499360",
    "indexes": [],
    "listRule": null,
    "name": "providers",
    "system": false,
    "type": "view",
    "updateRule": null,
    "viewQuery": "SELECT provider.mobile, provider.name, provider.id, provider.access FROM users AS provider WHERE provider.access like '%a00dg6c94sm0kcj%'",
    "viewRule": null
  });

  return app.save(collection);
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2443499360");

  return app.delete(collection);
})
