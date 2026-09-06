/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  // [fix/production-readiness] تایپوی "inavtive" در مقادیر select اصلاح شد.
  const status = collection.fields.getByName("status")
  status.values = ["active", "inactive"]

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2558321696")

  const status = collection.fields.getByName("status")
  status.values = ["active", "inavtive"]

  return app.save(collection)
})
