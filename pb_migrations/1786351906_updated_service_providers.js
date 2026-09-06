/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "name": "service_assignments"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_662238796")

  // update collection data
  unmarshal({
    "name": "service_providers"
  }, collection)

  return app.save(collection)
})
