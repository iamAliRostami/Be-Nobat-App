/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_351489016")

  // update collection data
  unmarshal({
    "indexes": [
      "CREATE INDEX `idx_rec1ga1p5g` ON `reputation_events` (`subject_user_id`)",
      "CREATE INDEX `idx_g7ffub5gxv` ON `reputation_events` (`type`)"
    ]
  }, collection)

  // update field
  collection.fields.addAt(5, new Field({
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
      "appointment_completed",
      "on_time",
      "late_arrival",
      "late_cancellation",
      "no_show",
      "cancelled_on_time",
      "positive_staff_feedback",
      "negative_staff_feedback",
      "appointment_cancelled",
      "review_received"
    ]
  }))

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_351489016")

  // update collection data
  unmarshal({
    "indexes": []
  }, collection)

  // update field
  collection.fields.addAt(5, new Field({
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
      "appointment_completed",
      "on_time",
      "late_arrival",
      "late_cancellation",
      "no_show",
      "cancelled_on_time",
      "positive_staff_feedback",
      "negative_staff_feedback"
    ]
  }))

  return app.save(collection)
})
