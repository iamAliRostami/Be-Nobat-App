/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "name": "resource_availability"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1041817932")

  // update collection data
  unmarshal({
    "name": "working_hours"
  }, collection)

  return app.save(collection)
})
