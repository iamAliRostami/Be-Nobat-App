package com.leon.be_nobat.ui

import android.text.InputType
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.R
import com.leon.be_nobat.data.objects
import com.leon.be_nobat.data.strings
import com.leon.be_nobat.data.text
import com.leon.be_nobat.domain.BookingRules
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Native management forms and calendars use server scoped records and named entity selectors. */
class ManagementScreens(private val host: MainActivity) {
    private val vm get() = host.vm
    private val screen get() = host.screen
    private fun draft(key: String, default: String = "") = host.draft(key, default)
    private fun context() = runCatching { JSONObject(draft("management_context")) }.getOrDefault(JSONObject())
    private fun roles() = vm.user.strings("roles")
    fun isPlatform() = roles().any { it == "PlatformAdmin" }
    fun canManage() = roles().any { it in listOf("Staff", "Manager", "Owner", "PlatformAdmin") }
    private fun canEdit() = if (context().has("canManageBusiness")) context().optBoolean("canManageBusiness") else roles().any { it in listOf("Manager", "Owner", "PlatformAdmin") }
    fun canRate(row: JSONObject) = canEditRow(row, "appointments")
    private fun canEditRow(row: JSONObject, resource: String): Boolean {
        if (isPlatform()) return true
        // Business-wide schedules are inherited by branches, but only platform administrators can change them.
        if (resource == "availability" && row.text("branchId").isBlank()) return false
        val memberships = context().objects("memberships").filter { it.optBoolean("canManageBusiness") }
        val branch = if (resource == "branches") row.text("id") else row.text("branchId")
        if (branch.isNotBlank()) return memberships.any { it.text("branchId") == branch }
        val business = if (resource == "businesses") row.text("id") else row.text("businessId")
        return business.isNotBlank() && memberships.any { it.text("businessId") == business }
    }
    private fun owner() = roles().any { it in listOf("Owner", "PlatformAdmin") }
    fun label(resource: String) = when (resource.substringAfter('/')) {
        "businesses" -> R.string.business; "branches" -> R.string.branches; "services" -> R.string.services; "resources" -> R.string.resources
        "memberships" -> R.string.memberships; "availability" -> R.string.availability; "appointments" -> R.string.appointments
        "reviews" -> R.string.reviews; "customer-reviews" -> R.string.customer_reviews; "catalog" -> R.string.catalog
        "categories" -> R.string.categories; "users" -> R.string.users; "branch-services" -> R.string.branch_services; "service-resources" -> R.string.service_resources
        else -> R.string.management
    }
    fun load(target: Screen) {
        when (target.name) {
            "manage", "platform" -> vm.load {
                val bootstrap = vm.api.request("admin/context")
                vm.drafts["management_context"] = bootstrap.toString()
                vm.api.request("admin/dashboard").put("context", bootstrap)
            }
            "calendar" -> vm.load {
                vm.api.request("admin/calendar", query = filters() + mapOf("date" to draft("calendar_date", LocalDate.now().toString()), "pageSize" to "100"))
                    .put("branches", vm.api.request("admin/branches", query = mapOf("pageSize" to "100")).optJSONArray("items"))
            }
            "manage_list" -> vm.load {
                val data = vm.api.request(target.id, query = filters() + mapOf("pageSize" to "100", "q" to draft("manage_q"), "includeInactive" to draft("manage_inactive", "false")))
                data.put("businesses", vm.api.request("admin/businesses", query = mapOf("pageSize" to "100")).optJSONArray("items"))
                data.put("branches", vm.api.request("admin/branches", query = mapOf("pageSize" to "100")).optJSONArray("items"))
            }
            "manage_form" -> vm.load { formData(target) }
        }
    }
    private fun filters() = mapOf("businessId" to draft("manage_business"), "branchId" to draft("manage_branch"), "status" to draft("manage_status"))
    private suspend fun formData(target: Screen): JSONObject {
        val row = runCatching { JSONObject(target.context) }.getOrDefault(JSONObject())
        val data = JSONObject().put("row", row)
        data.put("businesses", vm.api.request("admin/businesses", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        data.put("branches", vm.api.request("admin/branches", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        val resource = target.id.substringAfter('/')
        if (resource in listOf("services", "availability", "branch-services", "service-resources")) data.put("services", vm.api.request("admin/services", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        if (resource in listOf("availability", "service-resources")) data.put("resources", vm.api.request("admin/resources", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        if (resource in listOf("resources", "memberships")) data.put("team", vm.api.request("admin/team", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        if (target.id == "platform/businesses") data.put("users", vm.api.request("platform/users", query = mapOf("pageSize" to "100")).optJSONArray("items"))
        if (resource == "branch-services" && row.text("branchId").isNotBlank()) data.put("assigned", vm.api.request("admin/branches/${row.text("branchId")}/services").optJSONArray("items"))
        if (resource == "service-resources" && row.text("resourceId").isNotBlank()) data.put("assignedServiceIds", vm.api.request("admin/resources/${row.text("resourceId")}/services").optJSONArray("serviceIds"))
        if (resource == "memberships" && row.text("userId").isNotBlank()) data.put("accounts", JSONArray().put(JSONObject().put("id", row.text("userId")).put("displayName", row.text("displayName")).put("email", row.text("email"))))
        return data
    }
    fun render(state: NativeState) {
        when (state.screen.name) {
            "manage", "platform" -> dashboard(state.data, state.screen.name == "platform")
            "calendar" -> calendar(state.data)
            "manage_list" -> list(state.data)
            "manage_form" -> form(state.data)
        }
    }
    private fun dashboard(data: JSONObject, platform: Boolean) {
        host.add(host.text(host.getString(R.string.manage_hint), 16))
        val metrics = listOf(R.string.business to "businesses", R.string.branches to "branches", R.string.appointments to "appointments", R.string.pending to "pending", R.string.completed to "completed")
        metrics.forEach { (label, key) -> host.add(host.text(host.getString(label) + ": " + data.optInt(key), 18, true)) }
        host.add(host.button(R.string.calendar) { host.open(Screen("calendar")) })
        val routes = if (platform) listOf("platform/businesses", "platform/catalog", "platform/categories", "platform/users")
        else if (canEdit()) listOf("admin/businesses", "admin/branches", "admin/resources", "admin/services", "admin/memberships", "admin/availability", "admin/reviews", "admin/customer-reviews") else emptyList()
        routes.forEach { route -> host.add(host.button(label(route), outlined = true) { vm.drafts.remove("manage_status"); host.open(Screen("manage_list", route)) }) }
        if (!platform && isPlatform()) host.add(host.button(R.string.platform, outlined = true) { host.open(Screen("platform")) })
    }
    private fun scopeSelectors(parent: LinearLayout, data: JSONObject, business: Boolean = true) {
        if (business) host.choice(parent, R.string.business, listOf("" to host.getString(R.string.none)) + data.objects("businesses").map { it.text("id") to it.text("name") }, draft("manage_business")) {
            vm.drafts["manage_business"] = it; vm.drafts.remove("manage_branch"); host.load(screen)
        }
        val branches = data.objects("branches").filter { draft("manage_business").isBlank() || it.text("businessId") == draft("manage_business") }
        host.choice(parent, R.string.branch, listOf("" to host.getString(R.string.none)) + branches.map { it.text("id") to it.text("name") }, draft("manage_branch")) { vm.drafts["manage_branch"] = it; host.load(screen) }
    }
    private fun calendar(data: JSONObject) {
        host.add(host.text(host.getString(R.string.calendar_hint)))
        host.dateChoice(host.body(), "calendar_date") { host.load(screen) }
        scopeSelectors(host.body(), data, false)
        host.choice(host.body(), R.string.status, listOf("" to host.getString(R.string.all_statuses)) + host.statusOptions(), draft("manage_status")) { vm.drafts["manage_status"] = it; host.load(screen) }
        host.add(host.button(R.string.refresh, outlined = true) { host.load(screen) })
        if (data.objects().isEmpty()) host.add(host.text(host.getString(R.string.empty)))
        data.objects().sortedBy { it.text("startsAt") }.forEach { host.appointmentCard(host.body(), it, true) }
        host.paginated(data, "admin/calendar", filters() + mapOf("date" to draft("calendar_date", LocalDate.now().toString())))
    }
    private fun list(data: JSONObject) {
        val resource = screen.id.substringAfter('/')
        host.field(host.body(), R.string.search, "manage_q")
        host.add(host.button(R.string.search, outlined = true) { host.load(screen) })
        if (resource in listOf("businesses", "branches", "services", "resources", "catalog", "categories", "reviews")) host.check(host.body(), host.getString(R.string.show_inactive), draft("manage_inactive") == "true") { vm.drafts["manage_inactive"] = it.toString(); host.load(screen) }
        if (screen.id.startsWith("admin/") && resource !in listOf("businesses", "customer-reviews")) scopeSelectors(host.body(), data)
        val editable = canEdit() && resource !in listOf("reviews", "customer-reviews")
        val creatable = editable && screen.id != "admin/businesses"
        if (creatable) host.add(host.button(R.string.add) { edit(screen.id, JSONObject()) })
        if (data.objects().isEmpty()) host.add(host.text(host.getString(R.string.empty)))
        data.objects().forEach { row -> host.card(host.body()) {
            addView(host.text(row.text("name").ifBlank { row.text("displayName").ifBlank { row.text("customerName").ifBlank { host.getString(label(screen.id)) } } }, 20, true))
            val lines = when (resource) {
                "businesses" -> listOf(row.text("city"), row.text("category"), row.text("description"))
                "branches" -> listOf(row.text("address"), host.getString(R.string.branch_timezone, row.text("timeZoneId")))
                "services" -> listOf(row.text("description"), host.getString(R.string.price_duration, host.money(row.opt("price"), row.text("currency")), row.optInt("durationMinutes")))
                "memberships" -> listOf(row.text("email"), roleLabel(row.text("role")), name(data, "branches", row.text("branchId")))
                "resources" -> listOf(kindLabel(row.text("kind")), name(data, "branches", row.text("branchId")))
                "availability" -> listOf(row.text("effectiveDate"), dayLabel(row.text("dayOfWeek")), row.text("startsAt") + " – " + row.text("endsAt"), name(data, "branches", row.text("branchId")))
                "reviews", "customer-reviews" -> listOf(host.stars(row.optInt("rating")), row.text("comment"), host.statusLabel(row.text("status")), row.text("managerReply"))
                "users" -> listOf(row.text("email"), row.text("phoneNumber"), row.strings("roles").joinToString("، ") { roleLabel(it) })
                "categories" -> listOf(host.getString(if (row.text("kind") == "Service") R.string.service_category else R.string.business_category))
                "catalog" -> listOf(row.text("category"), row.text("description"), host.getString(R.string.duration_format, row.optInt("suggestedDurationMinutes")))
                else -> listOf(row.text("description"), row.text("kind"))
            }
            lines.filter(String::isNotBlank).forEach { addView(host.text(it)) }
            if (row.has("active") && !row.optBoolean("active")) addView(host.text(host.getString(R.string.inactive), 16, true))
            val rowEditable = canEditRow(row, resource)
            if (editable && rowEditable) addView(host.button(R.string.edit, outlined = true) { edit(screen.id, row) })
            if (resource == "reviews") addView(host.button(R.string.moderate, outlined = true) { moderation(row) })
            if (resource == "customer-reviews") addView(host.button(R.string.edit, outlined = true) { host.reviewDialog(row) { rating, comment -> vm.action { vm.api.request("admin/customer-reviews/${row.text("id")}", "PUT", JSONObject().put("appointmentId", row.text("appointmentId")).put("rating", rating).put("comment", comment)); host.load(screen) } } })
            if (resource == "branches" && rowEditable) {
                addView(host.button(R.string.branch_services, outlined = true) { edit("admin/branch-services", JSONObject().put("branchId", row.text("id"))) })
                addView(host.button(R.string.is_active, outlined = true) { setActive(screen.id, row) })
            }
            if (resource == "resources" && rowEditable && row.text("kind") == "staff") addView(host.button(R.string.service_resources, outlined = true) { edit("admin/service-resources", JSONObject().put("resourceId", row.text("id"))) })
            if (screen.id == "platform/users") {
                addView(host.button(R.string.roles, outlined = true) { userRoles(row) })
                addView(host.button(R.string.is_active, outlined = true) { setActive(screen.id, row) })
                addView(host.button(R.string.change_password, outlined = true) { resetPassword(row) })
            }
            if (screen.id == "platform/businesses") addView(host.button(R.string.is_active, outlined = true) { setActive(screen.id, row) })
            if (resource in listOf("services", "resources", "catalog", "categories", "reviews") && rowEditable) addView(host.button(R.string.is_active, outlined = true) { setActive(screen.id, row) })
            if (rowEditable && resource !in listOf("users") && (resource != "businesses" || screen.id.startsWith("platform/"))) addView(host.button(R.string.delete, outlined = true) { delete(screen.id, row.text("id")) })
        } }
        host.paginated(data, screen.id, filters() + mapOf("q" to draft("manage_q"), "includeInactive" to draft("manage_inactive", "false")))
    }
    private fun name(data: JSONObject, collection: String, id: String) = data.objects(collection).firstOrNull { it.text("id") == id }?.text("name").orEmpty()
    private fun edit(route: String, row: JSONObject) {
        vm.drafts.keys.filter { it.startsWith("edit_") }.toList().forEach(vm.drafts::remove)
        host.open(Screen("manage_form", route, row.toString()))
    }
    private fun delete(route: String, id: String) {
        MaterialAlertDialogBuilder(host).setMessage(R.string.delete_confirm).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
            vm.action { vm.api.request("$route/$id", "DELETE"); host.load(screen) }
        }.show()
    }
    private fun setActive(route: String, row: JSONObject) {
        var active = row.optBoolean("active", true)
        val form = host.column().apply { setPadding(host.dp(24), 0, host.dp(24), 0) }
        host.check(form, host.getString(R.string.is_active), active) { active = it }
        MaterialAlertDialogBuilder(host).setTitle(R.string.is_active).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ ->
            vm.action { vm.api.request("$route/${row.text("id")}/active", "PUT", JSONObject().put("active", active)); host.load(screen) }
        }.show()
    }
    fun changeAppointmentStatus(row: JSONObject) {
        val choices = BookingRules.statusActions(row.text("status"), row.text("startsAt")).map { it to host.statusLabel(it) }
        MaterialAlertDialogBuilder(host).setTitle(R.string.status).setItems(choices.map { it.second }.toTypedArray()) { _, index ->
            vm.action { vm.api.request("admin/appointments/${row.text("id")}/status", "PUT", JSONObject().put("status", choices[index].first)); host.load(screen) }
        }.setNegativeButton(R.string.cancel, null).show()
    }
    fun rateCustomer(row: JSONObject) {
        host.reviewDialog { rating, comment -> vm.action {
            vm.api.request("admin/customer-reviews", "POST", JSONObject().put("appointmentId", row.text("id")).put("rating", rating).put("comment", comment))
            host.toast(R.string.saved); host.load(screen)
        } }
    }
    private fun moderation(row: JSONObject) {
        val form = host.column().apply { setPadding(host.dp(24), 0, host.dp(24), 0) }
        var status = row.text("status")
        host.choice(form, R.string.status, listOf("Pending", "Published", "Rejected").map { it to host.statusLabel(it) }, status) { status = it }
        vm.drafts.remove("moderation_reply")
        val reply = host.field(form, R.string.manager_reply, "moderation_reply", row.text("managerReply"))
        MaterialAlertDialogBuilder(host).setTitle(R.string.moderate).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ -> vm.action {
            vm.api.request("admin/reviews/${row.text("id")}/moderation", "PUT", JSONObject().put("status", status).put("managerReply", reply.text.toString()))
            host.load(screen)
        } }.show()
    }
    private fun userRoles(row: JSONObject) {
        val form = host.column().apply { setPadding(host.dp(24), 0, host.dp(24), 0) }
        val selected = row.strings("roles").filter { it in listOf("Customer", "PlatformAdmin") }.toMutableSet()
        row.strings("roles").filter { it in listOf("Owner", "Manager", "Staff") }.forEach { form.addView(host.text(roleLabel(it))) }
        listOf("Customer", "PlatformAdmin").forEach { role -> host.check(form, roleLabel(role), role in selected) { if (it) selected.add(role) else selected.remove(role) } }
        MaterialAlertDialogBuilder(host).setTitle(R.string.roles).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ -> vm.action {
            vm.api.request("platform/users/${row.text("id")}/roles", "PUT", JSONObject().put("roles", JSONArray(selected.toList()))); host.load(screen)
        } }.show()
    }
    private fun resetPassword(row: JSONObject) {
        MaterialAlertDialogBuilder(host).setMessage(R.string.reset_password_confirm).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm) { _, _ -> vm.action {
            val result = vm.api.request("platform/users/${row.text("id")}/reset-password", "POST")
            temporaryPassword(result.text("temporaryPassword"))
        } }.show()
    }
    private fun temporaryPassword(password: String) {
        if (password.isBlank()) return
        val dialog = MaterialAlertDialogBuilder(host).setTitle(R.string.password)
            .setMessage(host.getString(R.string.credentials_once) + "\n\n" + host.getString(R.string.temporary_password, password))
            .setPositiveButton(R.string.close, null).setNeutralButton(R.string.copy) { _, _ ->
                val clipboard = host.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(host.getString(R.string.password), password)
                val extra = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                clip.description.extras = extra
                clipboard.setPrimaryClip(clip); host.toast(R.string.copied)
            }.create()
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private data class Field(val key: String, val label: Int, val kind: String = "text", val default: String = "", val required: Boolean = false)
    private fun form(data: JSONObject) {
        val row = data.optJSONObject("row") ?: return
        val resource = screen.id.substringAfter('/')
        if (resource == "branch-services") { branchServices(data, row); return }
        if (resource == "service-resources") { resourceServices(data, row); return }
        val editing = row.text("id").isNotBlank()
        host.add(host.text(host.getString(if (editing) R.string.edit else R.string.add), 24, true))
        val fields = when (resource) {
            "businesses" -> listOf(Field("name", R.string.name, required = true), Field("slug", R.string.slug), Field("category", R.string.category, required = true), Field("city", R.string.city, required = true), Field("description", R.string.description), Field("requiresApproval", R.string.requires_approval, "bool", "true")) +
                if (screen.id.startsWith("platform/") && !editing) listOf(Field("firstBranchName", R.string.branch, default = host.getString(R.string.branch), required = true), Field("ownerId", R.string.user, "users")) else emptyList()
            "branches" -> listOf(Field("businessId", R.string.business, "businesses", draft("manage_business"), true), Field("name", R.string.name, required = true), Field("address", R.string.address), Field("timeZoneId", R.string.time_zone, default = "Asia/Tehran", required = true), Field("openHour", R.string.open_hour, "int", "9", true), Field("closeHour", R.string.close_hour, "int", "18", true))
            "resources" -> listOf(Field("branchId", R.string.branch, "branches", draft("manage_branch"), true), Field("name", R.string.name, required = true), Field("kind", R.string.kind, "resourceKind", "staff", true), Field("userId", R.string.user, "team"))
            "memberships" -> listOf(Field("branchId", R.string.branch, "branches", draft("manage_branch"), true), Field("userId", R.string.user, "accounts", required = true), Field("role", R.string.role, "role", "Staff", true))
            "services" -> listOf(Field("businessId", R.string.business, "businesses", draft("manage_business"), true), Field("name", R.string.name, required = true), Field("description", R.string.description), Field("durationMinutes", R.string.duration, "int", "30", true), Field("price", R.string.price, "decimal", "0", true), Field("currency", R.string.currency, default = "IRR", required = true))
            "availability" -> listOf(Field("businessId", R.string.business, "businesses", draft("manage_business"), true), Field("branchId", R.string.branch, "branches", draft("manage_branch"), !isPlatform()), Field("resourceId", R.string.provider, "resources"), Field("effectiveDate", R.string.effective_date, "date", LocalDate.now().plusDays(1).toString(), true), Field("startsAt", R.string.starts_at, "time", "09:00:00", true), Field("endsAt", R.string.ends_at, "time", "18:00:00", true), Field("isAvailable", R.string.is_available, "bool", "true"))
            "catalog" -> listOf(Field("name", R.string.name, required = true), Field("slug", R.string.slug), Field("category", R.string.category, required = true), Field("description", R.string.description), Field("suggestedDurationMinutes", R.string.suggested_duration, "int", "30", true), Field("isPublished", R.string.is_published, "bool", "true"))
            "categories" -> listOf(Field("name", R.string.name, required = true), Field("kind", R.string.kind, "categoryKind", "Business", true), Field("sortOrder", R.string.sort_order, "int", "0"), Field("isActive", R.string.is_active, "bool", "true"))
            "users" -> listOf(Field("displayName", R.string.display_name, required = true), Field("email", R.string.email, required = true), Field("phoneNumber", R.string.phone_number, required = true)) + if (!editing) listOf(Field("role", R.string.role, "systemRole", "Customer", true)) else emptyList()
            else -> emptyList()
        }
        if (resource == "memberships" && !editing) {
            val email = host.field(host.body(), R.string.email, "edit_account_email", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            host.add(host.button(R.string.search, outlined = true) { vm.load {
                val accounts = vm.api.request("admin/accounts", query = mapOf("email" to email.text.toString().trim()))
                data.put("accounts", accounts.optJSONArray("items"))
            } })
        }
        val secretInputs = mutableMapOf<String, com.google.android.material.textfield.TextInputEditText>()
        fields.forEach { field ->
            val key = "edit_${field.key}"
            val initial = if (row.has(field.key) && !row.isNull(field.key)) row.opt(field.key)?.toString().orEmpty() else if (field.key == "isActive" && row.has("active")) row.optBoolean("active").toString() else field.default
            vm.drafts.putIfAbsent(key, initial)
            when (field.kind) {
                "bool" -> host.check(host.body(), host.getString(field.label), draft(key, initial).toBoolean()) { vm.drafts[key] = it.toString() }
                "date" -> host.dateChoice(host.body(), key, field.label) { host.body().removeAllViews(); form(data) }
                "time" -> host.timeChoice(host.body(), key, field.label)
                "businesses", "branches", "resources", "team", "users", "accounts" -> {
                    var options = data.objects(field.kind)
                    if (field.kind == "branches" && draft("edit_businessId").isNotBlank()) options = options.filter { it.text("businessId") == draft("edit_businessId") }
                    if (field.kind == "resources" && draft("edit_branchId").isNotBlank()) options = options.filter { it.text("branchId") == draft("edit_branchId") }
                    val choices = (if (field.required) emptyList() else listOf("" to host.getString(R.string.none))) + options.map { it.text("id") to it.text("name").ifBlank { it.text("displayName") + " · " + it.text("email") } }
                    host.choice(host.body(), field.label, choices, draft(key)) { vm.drafts[key] = it
                        if (field.key == "businessId" || field.key == "branchId") { host.body().removeAllViews(); form(data) }
                    }
                }
                "role", "systemRole", "resourceKind", "categoryKind", "day" -> host.choice(host.body(), field.label, choices(field.kind), draft(key)) { vm.drafts[key] = it }
                else -> {
                    val edit = host.field(host.body(), field.label, key, initial, when (field.kind) {
                    "int" -> InputType.TYPE_CLASS_NUMBER; "decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; else -> InputType.TYPE_CLASS_TEXT
                    }, persist = field.kind != "password")
                    if (field.kind == "password") secretInputs[field.key] = edit
                }
            }
        }
        host.add(host.button(R.string.save) {
            val body = JSONObject()
            var valid = true
            fields.forEach { field ->
                val value = secretInputs[field.key]?.text?.toString() ?: draft("edit_${field.key}")
                if (field.required && value.isBlank()) valid = false
                when (field.kind) {
                    "bool" -> body.put(field.key, value.toBoolean())
                    "int" -> { val number = value.toIntOrNull(); if (number == null) valid = false else body.put(field.key, number) }
                    "decimal" -> { val number = value.toBigDecimalOrNull(); if (number == null) valid = false else body.put(field.key, number) }
                    else -> body.put(field.key, if (value.isBlank() && field.key.endsWith("Id")) JSONObject.NULL else if (field.key in listOf("startsAt", "endsAt") && value.length == 5) "$value:00" else if (field.key == "effectiveDate" && value.isBlank()) JSONObject.NULL else value)
                }
            }
            if (!valid) { vm.showError("required"); return@button }
            vm.action {
                val result = vm.api.request(screen.id + if (editing) "/${row.text("id")}" else "", if (editing) "PUT" else "POST", body)
                secretInputs.values.forEach { it.text?.clear() }
                host.toast(R.string.saved); host.open(Screen("manage_list", screen.id), false)
                if (result.text("temporaryPassword").isNotBlank()) temporaryPassword(result.text("temporaryPassword"))
            }
        })
    }
    private fun choices(kind: String): List<Pair<String, String>> = when (kind) {
        "role" -> (if (owner()) listOf("Owner", "Manager", "Staff") else listOf("Manager", "Staff")).map { it to roleLabel(it) }
        "systemRole" -> listOf("Customer", "PlatformAdmin").map { it to roleLabel(it) }
        "resourceKind" -> listOf("staff" to host.getString(R.string.staff), "room" to host.getString(R.string.room), "equipment" to host.getString(R.string.equipment))
        "categoryKind" -> listOf("Business" to host.getString(R.string.business_category), "Service" to host.getString(R.string.service_category))
        "day" -> listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").map { it to dayLabel(it) }
        else -> emptyList()
    }
    private fun roleLabel(role: String) = host.getString(when (role) { "Owner" -> R.string.business_admin; "Manager" -> R.string.manager; "Staff" -> R.string.staff; "PlatformAdmin" -> R.string.platform_admin; else -> R.string.customer })
    private fun kindLabel(kind: String) = host.getString(when (kind) { "room" -> R.string.room; "equipment" -> R.string.equipment; else -> R.string.staff })
    private fun dayLabel(day: String) = host.getString(when (day) { "Sunday", "0" -> R.string.sunday; "Monday", "1" -> R.string.monday; "Tuesday", "2" -> R.string.tuesday; "Wednesday", "3" -> R.string.wednesday; "Thursday", "4" -> R.string.thursday; "Friday", "5" -> R.string.friday; else -> R.string.saturday })
    private fun branchServices(data: JSONObject, row: JSONObject) {
        host.add(host.text(host.getString(R.string.service_assignment_hint)))
        val branch = data.objects("branches").firstOrNull { it.text("id") == row.text("branchId") }
        host.add(host.text(branch?.text("name").orEmpty(), 24, true))
        val services = data.objects("services").filter { it.text("businessId") == branch?.text("businessId") }
        val assigned = data.objects("assigned")
        services.forEach { service -> host.card(host.body()) {
            var enabled = assigned.any { it.text("serviceId") == service.text("id") || it.text("id") == service.text("id") }
            host.check(this, service.text("name"), enabled) { enabled = it }
            val price = host.field(this, R.string.price, "edit_price_${service.text("id")}", (assigned.firstOrNull { it.text("serviceId") == service.text("id") || it.text("id") == service.text("id") }?.text("price") ?: service.text("price")), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            addView(host.button(R.string.save, outlined = true) { vm.action {
                val path = "admin/branches/${row.text("branchId")}/services/${service.text("id")}"
                if (enabled) vm.api.request(path, "PUT", JSONObject().put("price", price.text.toString().toBigDecimalOrNull() ?: JSONObject.NULL)) else vm.api.request(path, "DELETE")
                host.toast(R.string.saved); host.load(screen)
            } })
        } }
    }
    private fun resourceServices(data: JSONObject, row: JSONObject) {
        host.add(host.text(host.getString(R.string.service_assignment_hint)))
        val provider = data.objects("resources").firstOrNull { it.text("id") == row.text("resourceId") }
        host.add(host.text(provider?.text("name").orEmpty(), 24, true))
        val branch = data.objects("branches").firstOrNull { it.text("id") == provider?.text("branchId") }
        val assigned = data.strings("assignedServiceIds").toMutableSet()
        val services = data.objects("services").filter { it.text("businessId") == branch?.text("businessId") && branch?.text("id") in it.strings("branchIds") }
        services.forEach { service -> host.check(host.body(), service.text("name"), service.text("id") in assigned) { if (it) assigned.add(service.text("id")) else assigned.remove(service.text("id")) } }
        host.add(host.button(R.string.save) { vm.action {
            vm.api.request("admin/resources/${row.text("resourceId")}/services", "PUT", JSONObject().put("serviceIds", JSONArray(assigned.toList())))
            host.toast(R.string.saved)
        } })
    }
}
