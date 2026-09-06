/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "name": "roles"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_830101018")

  // update collection data
  unmarshal({
    "name": "access"
  }, collection)

  return app.save(collection)
})
