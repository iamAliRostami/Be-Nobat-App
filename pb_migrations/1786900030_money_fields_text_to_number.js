/// <reference path="../pb_data/types.d.ts" />

// [fix/production-readiness]
// این هلپر یک فیلد text را با یک فیلد number با همان id/name/required جایگزین
// می‌کند. id واقعیِ فیلد و موقعیتش در لیست، در لحظه‌ی اجرا از روی نام فیلد
// خوانده می‌شود (نه هاردکد)، تا این migration مستقل از اینکه id فعلی در
// دیتابیس شما دقیقاً چی هست کار کند.
function convertTextToNumber(collection, fieldName, opts) {
  const existing = collection.fields.getByName(fieldName)
  const existingId = existing.id
  const wasRequired = existing.required

  let index = -1
  for (let i = 0; i < collection.fields.length; i++) {
    if (collection.fields[i].name === fieldName) {
      index = i
      break
    }
  }

  collection.fields.removeByName(fieldName)

  collection.fields.addAt(index >= 0 ? index : collection.fields.length, new Field({
    "help": "",
    "hidden": false,
    "id": existingId,
    "max": null,
    "min": (opts && typeof opts.min === "number") ? opts.min : 0,
    "name": fieldName,
    "onlyInt": false,
    "presentable": false,
    "required": wasRequired,
    "system": false,
    "type": "number"
  }))
}

function convertNumberToText(collection, fieldName, opts) {
  const existing = collection.fields.getByName(fieldName)
  const existingId = existing.id
  const wasRequired = existing.required

  let index = -1
  for (let i = 0; i < collection.fields.length; i++) {
    if (collection.fields[i].name === fieldName) {
      index = i
      break
    }
  }

  collection.fields.removeByName(fieldName)

  collection.fields.addAt(index >= 0 ? index : collection.fields.length, new Field({
    "autogeneratePattern": "",
    "help": "",
    "hidden": false,
    "id": existingId,
    "max": 0,
    "min": (opts && typeof opts.min === "number") ? opts.min : 0,
    "name": fieldName,
    "pattern": "^(0|[1-9]\\d*)$",
    "presentable": false,
    "primaryKey": false,
    "required": wasRequired,
    "system": false,
    "type": "text"
  }))
}

migrate((app) => {
  const appointment = app.findCollectionByNameOrId("pbc_1970990732")
  convertTextToNumber(appointment, "total_price")
  convertTextToNumber(appointment, "discount_amount")
  convertTextToNumber(appointment, "final_price")
  app.save(appointment)

  const appointmentServices = app.findCollectionByNameOrId("pbc_1262204345")
  convertTextToNumber(appointmentServices, "price")
  return app.save(appointmentServices)
}, (app) => {
  const appointment = app.findCollectionByNameOrId("pbc_1970990732")
  convertNumberToText(appointment, "total_price", { min: 0 })
  convertNumberToText(appointment, "discount_amount", { min: 0 })
  convertNumberToText(appointment, "final_price", { min: 0 })
  app.save(appointment)

  const appointmentServices = app.findCollectionByNameOrId("pbc_1262204345")
  convertNumberToText(appointmentServices, "price", { min: 1 })
  return app.save(appointmentServices)
})
