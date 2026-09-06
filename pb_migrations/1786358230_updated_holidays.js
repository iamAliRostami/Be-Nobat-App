/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // update collection data
  unmarshal({
    "name": "resource_exceptions"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2049505557")

  // update collection data
  unmarshal({
    "name": "holidays"
  }, collection)

  return app.save(collection)
})
