/// <reference path="../pb_data/types.d.ts" />

migrate((app) => {
  const collection = app.findCollectionByNameOrId("appointment")

  // مشتری فقط می‌تواند نوبت خودش را cancel کند؛ state machine هوک 40 نیز
  // گذار را به pending/confirmed محدود می‌کند. فیلدهای محاسباتی همچنان قفل‌اند.
  const previous = collection.updateRule
  const clientCancellation = `
client_user_id = @request.auth.id &&
@request.body.client_user_id:changed = false &&
@request.body.branch_id:changed = false &&
@request.body.total_price:changed = false &&
@request.body.discount_amount:changed = false &&
@request.body.final_price:changed = false &&
@request.body.start:changed = false &&
@request.body.end:changed = false &&
@request.body.status:changed = true &&
@request.body.status = "cancelled"
`.trim()

  collection.updateRule = "(" + previous + ") || (" + clientCancellation + ")"
  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("appointment")
  // Rule دقیق پیش از این migration (برای rollback قطعی و قابل بازتولید).
  collection.updateRule = `(
    client_user_id = @request.auth.id &&
    @request.body.client_user_id:changed = false &&
    @request.body.branch_id:changed = false &&
    @request.body.total_price:changed = false &&
    @request.body.discount_amount:changed = false &&
    @request.body.final_price:changed = false &&
    @request.body.start:changed = false &&
    @request.body.end:changed = false &&
    @request.body.status:changed = false
)
|| branch_id.business_id.owner_user_id = @request.auth.id
|| (
    @collection.branch_membership.branch_id ?= branch_id &&
    @collection.branch_membership.user_id ?= @request.auth.id &&
    @collection.branch_membership.status ?= "active"
)
|| (
    @collection.business_membership.business_id ?= branch_id.business_id &&
    @collection.business_membership.user_id ?= @request.auth.id &&
    @collection.business_membership.status ?= "active"
)`
  return app.save(collection)
})
